from __future__ import annotations

import hashlib
import io
import json
import os
import struct
import zipfile
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Iterable

from .constants import (
    APP_ENGLISH_NAME,
    DEFAULT_MEDIA_PACKAGE_SUFFIX,
    MEDIA_PACKAGE_FORMAT,
    MEDIA_PACKAGE_MAGIC,
    MEDIA_PACKAGE_VERSION,
)
from .models import classify_media_type

PBKDF2_ITERATIONS = 390_000
SALT_LEN = 16
NONCE_LEN = 12
DEFAULT_ACCESS_PHRASE = "MaoqiuPlayer local media package v1"


class MediaPackageError(Exception):
    pass


@dataclass(frozen=True, slots=True)
class MediaPackageInfo:
    path: Path
    package_name: str
    created_at: str
    item_count: int
    items: list[dict]
    suffix: str
    payload_size: int


def is_media_package(path: str | Path) -> bool:
    try:
        with Path(path).open("rb") as handle:
            return handle.read(len(MEDIA_PACKAGE_MAGIC)) == MEDIA_PACKAGE_MAGIC
    except OSError:
        return False


def read_media_package_info(path: str | Path) -> MediaPackageInfo:
    package_path, header, payload = _read_package(path)
    return MediaPackageInfo(
        path=package_path,
        package_name=header.get("package_name") or package_path.stem,
        created_at=header.get("created_at", ""),
        item_count=int(header.get("item_count", 0)),
        items=list(header.get("items", [])),
        suffix=package_path.suffix or DEFAULT_MEDIA_PACKAGE_SUFFIX,
        payload_size=len(payload),
    )


def verify_media_package(path: str | Path) -> bool:
    _, header, payload = _read_package(path)
    return hashlib.sha256(payload).hexdigest() == header.get("payload_sha256")


def create_media_package(
    input_paths: Iterable[str | Path],
    output_path: str | Path,
    package_name: str | None = None,
    access_phrase: str | None = None,
) -> MediaPackageInfo:
    paths = [Path(path).expanduser() for path in input_paths]
    files = [path for path in paths if path.exists() and path.is_file() and classify_media_type(path) in {"video", "image"}]
    if not files:
        raise MediaPackageError("请选择至少一个视频或图片文件。")

    output = Path(output_path).expanduser()
    if not output.suffix:
        output = output.with_suffix(DEFAULT_MEDIA_PACKAGE_SUFFIX)
    output.parent.mkdir(parents=True, exist_ok=True)

    zip_bytes, items = _build_zip_payload(files)
    salt = os.urandom(SALT_LEN)
    nonce = os.urandom(NONCE_LEN)
    key = _derive_key(access_phrase, salt)
    encrypted_payload = _aesgcm_encrypt(key, nonce, zip_bytes)

    header = {
        "format": MEDIA_PACKAGE_FORMAT,
        "version": MEDIA_PACKAGE_VERSION,
        "app": APP_ENGLISH_NAME,
        "package_name": package_name or output.stem,
        "created_at": datetime.now().isoformat(timespec="seconds"),
        "item_count": len(items),
        "items": items,
        "suffix": output.suffix,
        "cipher": "AES-256-GCM",
        "kdf": "PBKDF2-HMAC-SHA256",
        "iterations": PBKDF2_ITERATIONS,
        "salt": salt.hex(),
        "nonce": nonce.hex(),
        "payload_sha256": hashlib.sha256(encrypted_payload).hexdigest(),
    }
    header_bytes = json.dumps(header, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode("utf-8")
    with output.open("wb") as handle:
        handle.write(MEDIA_PACKAGE_MAGIC)
        handle.write(struct.pack(">I", len(header_bytes)))
        handle.write(header_bytes)
        handle.write(encrypted_payload)

    return read_media_package_info(output)


def extract_media_package(path: str | Path, output_dir: str | Path, access_phrase: str | None = None) -> list[Path]:
    package_path, header, encrypted_payload = _read_package(path)
    if hashlib.sha256(encrypted_payload).hexdigest() != header.get("payload_sha256"):
        raise MediaPackageError("媒体包校验失败，文件可能不完整。")
    salt = bytes.fromhex(header["salt"])
    nonce = bytes.fromhex(header["nonce"])
    key = _derive_key(access_phrase, salt)
    try:
        zip_bytes = _aesgcm_decrypt(key, nonce, encrypted_payload)
    except Exception as exc:
        raise MediaPackageError("无法打开该媒体包，请检查文件完整性或访问口令。") from exc

    target_dir = Path(output_dir).expanduser()
    target_dir.mkdir(parents=True, exist_ok=True)
    extracted: list[Path] = []
    resolved_target_dir = target_dir.resolve()
    with zipfile.ZipFile(io.BytesIO(zip_bytes), "r") as archive:
        for member in archive.infolist():
            target = (target_dir / member.filename).resolve()
            try:
                target.relative_to(resolved_target_dir)
            except ValueError as exc:
                raise MediaPackageError("媒体包目录结构无效。")
            if member.is_dir():
                continue
            archive.extract(member, target_dir)
            extracted.append(target)
    return extracted


def _read_package(path: str | Path) -> tuple[Path, dict, bytes]:
    package_path = Path(path).expanduser()
    try:
        with package_path.open("rb") as handle:
            magic = handle.read(len(MEDIA_PACKAGE_MAGIC))
            if magic != MEDIA_PACKAGE_MAGIC:
                raise MediaPackageError("不是有效的媒体包文件。")
            header_len_raw = handle.read(4)
            if len(header_len_raw) != 4:
                raise MediaPackageError("媒体包信息不完整。")
            header_len = struct.unpack(">I", header_len_raw)[0]
            header_bytes = handle.read(header_len)
            if len(header_bytes) != header_len:
                raise MediaPackageError("媒体包信息不完整。")
            payload = handle.read()
    except OSError as exc:
        raise MediaPackageError("无法读取该媒体包，请检查路径和权限。") from exc

    try:
        header = json.loads(header_bytes.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise MediaPackageError("媒体包信息损坏。") from exc
    if header.get("format") != MEDIA_PACKAGE_FORMAT or header.get("version") != MEDIA_PACKAGE_VERSION:
        raise MediaPackageError("不支持的媒体包版本。")
    return package_path, header, payload


def _build_zip_payload(files: list[Path]) -> tuple[bytes, list[dict]]:
    buffer = io.BytesIO()
    items: list[dict] = []
    used_names: set[str] = set()
    with zipfile.ZipFile(buffer, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for index, path in enumerate(files, start=1):
            arcname = _unique_archive_name(path.name, used_names, index)
            used_names.add(arcname)
            archive.write(path, arcname)
            items.append(
                {
                    "name": path.name,
                    "media_type": classify_media_type(path),
                    "size": path.stat().st_size,
                    "sha256": _sha256_file(path),
                    "path": arcname,
                }
            )
    return buffer.getvalue(), items


def _unique_archive_name(name: str, used_names: set[str], index: int) -> str:
    if name not in used_names:
        return name
    path = Path(name)
    return f"{path.stem}-{index}{path.suffix}"


def _derive_key(access_phrase: str | None, salt: bytes) -> bytes:
    phrase = access_phrase.strip() if access_phrase else DEFAULT_ACCESS_PHRASE
    return hashlib.pbkdf2_hmac("sha256", phrase.encode("utf-8"), salt, PBKDF2_ITERATIONS, dklen=32)


def _aesgcm_encrypt(key: bytes, nonce: bytes, payload: bytes) -> bytes:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM

    return AESGCM(key).encrypt(nonce, payload, MEDIA_PACKAGE_MAGIC)


def _aesgcm_decrypt(key: bytes, nonce: bytes, payload: bytes) -> bytes:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM

    return AESGCM(key).decrypt(nonce, payload, MEDIA_PACKAGE_MAGIC)


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()
