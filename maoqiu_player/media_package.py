from __future__ import annotations

import hashlib
import io
import json
import os
import struct
import tarfile
import tempfile
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
GCM_TAG_LEN = 16
DEFAULT_ACCESS_PHRASE = "MaoqiuPlayer local media package v1"

ENCRYPTOR_LITE_MAGIC = b"MAOLITE1\0"
ENCRYPTOR_LITE_FORMAT = "MAOQIU_LITE"
ENCRYPTOR_LITE_VERSION = 1
ENCRYPTOR_LITE_PAYLOAD_FILE = "file"
ENCRYPTOR_LITE_PAYLOAD_BUNDLE = "tar_bundle"
ENCRYPTOR_LITE_APP_KEY = hashlib.sha256(b"Maoqiu Secure Lite v1 built-in application key").digest()

ENCRYPTOR_SECURE_MAGIC = b"MAOQIU1\0"
ENCRYPTOR_SECURE_FORMAT = "MAOQIU"
ENCRYPTOR_SECURE_VERSION = 1
ENCRYPTOR_KEY_WRAP_PASSWORD = "password"
ENCRYPTOR_FILE_CIPHER_AES_128_GCM = "AES-128-GCM"
ENCRYPTOR_FILE_CIPHER_AES_192_GCM = "AES-192-GCM"
ENCRYPTOR_FILE_CIPHER_AES_GCM = "AES-256-GCM"
ENCRYPTOR_AES_GCM_FILE_CIPHERS = {
    ENCRYPTOR_FILE_CIPHER_AES_128_GCM,
    ENCRYPTOR_FILE_CIPHER_AES_192_GCM,
    ENCRYPTOR_FILE_CIPHER_AES_GCM,
}
ENCRYPTOR_FILE_CIPHER_CHACHA20 = "ChaCha20-Poly1305"


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
    format_name: str = MEDIA_PACKAGE_FORMAT
    requires_credentials: bool = False


def is_media_package(path: str | Path) -> bool:
    try:
        with Path(path).open("rb") as handle:
            prefix = handle.read(max(len(MEDIA_PACKAGE_MAGIC), len(ENCRYPTOR_LITE_MAGIC), len(ENCRYPTOR_SECURE_MAGIC)))
            return (
                prefix.startswith(MEDIA_PACKAGE_MAGIC)
                or prefix.startswith(ENCRYPTOR_LITE_MAGIC)
                or prefix.startswith(ENCRYPTOR_SECURE_MAGIC)
            )
    except OSError:
        return False


def read_media_package_info(path: str | Path) -> MediaPackageInfo:
    package_format = _detect_package_format(path)
    if package_format == "encryptor_lite":
        return _read_lite_package_info(path)
    if package_format == "encryptor_secure":
        return _read_secure_package_info(path)

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
    package_format = _detect_package_format(path)
    if package_format == "encryptor_lite":
        header, payload = _read_lite_container(path)
        try:
            plain = _decrypt_encryptor_payload(
                payload,
                ENCRYPTOR_LITE_APP_KEY,
                _b64decode(header["payload_nonce"]),
                header.get("cipher", ENCRYPTOR_FILE_CIPHER_AES_GCM),
            )
        except Exception as exc:
            raise MediaPackageError("轻量加密文件校验失败，文件可能不完整。") from exc
        return hashlib.sha256(plain).hexdigest() == header.get("original_sha256")
    if package_format == "encryptor_secure":
        raise MediaPackageError("该文件需要用户名和密码，无法在未解密时校验。")

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


def extract_media_package(
    path: str | Path,
    output_dir: str | Path,
    access_phrase: str | None = None,
    username: str = "",
    password: str = "",
) -> list[Path]:
    package_format = _detect_package_format(path)
    if package_format == "encryptor_lite":
        return _extract_lite_package(path, output_dir)
    if package_format == "encryptor_secure":
        if access_phrase and not password:
            username, _, password = access_phrase.partition("\n")
        return _extract_secure_package(path, output_dir, username=username, password=password)

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


def media_package_requires_credentials(path: str | Path) -> bool:
    return _detect_package_format(path) == "encryptor_secure"


def _detect_package_format(path: str | Path) -> str:
    try:
        with Path(path).expanduser().open("rb") as handle:
            prefix = handle.read(max(len(MEDIA_PACKAGE_MAGIC), len(ENCRYPTOR_LITE_MAGIC), len(ENCRYPTOR_SECURE_MAGIC)))
    except OSError as exc:
        raise MediaPackageError("无法读取该媒体包，请检查路径和权限。") from exc
    if prefix.startswith(MEDIA_PACKAGE_MAGIC):
        return "player"
    if prefix.startswith(ENCRYPTOR_LITE_MAGIC):
        return "encryptor_lite"
    if prefix.startswith(ENCRYPTOR_SECURE_MAGIC):
        return "encryptor_secure"
    raise MediaPackageError("不是有效的媒体包文件。")


def _read_prefixed_json_payload(path: str | Path, magic: bytes) -> tuple[Path, dict, bytes, bytes]:
    package_path = Path(path).expanduser()
    try:
        with package_path.open("rb") as handle:
            if handle.read(len(magic)) != magic:
                raise MediaPackageError("不是有效的媒体包文件。")
            header_len_raw = handle.read(4)
            if len(header_len_raw) != 4:
                raise MediaPackageError("媒体包信息不完整。")
            header_len = struct.unpack(">I", header_len_raw)[0]
            header_bytes = handle.read(header_len)
            if len(header_bytes) != header_len:
                raise MediaPackageError("媒体包信息不完整。")
            payload = handle.read()
    except (OSError, struct.error) as exc:
        raise MediaPackageError("无法读取该媒体包，请检查路径和权限。") from exc
    try:
        header = json.loads(header_bytes.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise MediaPackageError("媒体包信息损坏。") from exc
    return package_path, header, header_bytes, payload


def _read_lite_container(path: str | Path) -> tuple[dict, bytes]:
    _, header, _, payload = _read_prefixed_json_payload(path, ENCRYPTOR_LITE_MAGIC)
    if header.get("format") != ENCRYPTOR_LITE_FORMAT or header.get("version") != ENCRYPTOR_LITE_VERSION:
        raise MediaPackageError("不支持的轻量加密文件版本。")
    return header, payload


def _read_lite_package_info(path: str | Path) -> MediaPackageInfo:
    package_path = Path(path).expanduser()
    header, payload = _read_lite_container(package_path)
    payload_type = header.get("payload_type")
    if payload_type == ENCRYPTOR_LITE_PAYLOAD_BUNDLE:
        items = [
            {"name": name, "media_type": classify_media_type(name), "size": 0, "path": name}
            for name in header.get("bundle_items", [])
        ]
    else:
        filename = header.get("original_filename") or package_path.stem
        items = [
            {
                "name": filename,
                "media_type": classify_media_type(filename),
                "size": int(header.get("original_size", 0)),
                "path": filename,
            }
        ]
    return MediaPackageInfo(
        path=package_path,
        package_name=package_path.stem,
        created_at=header.get("created_time", ""),
        item_count=len(items),
        items=items,
        suffix=package_path.suffix,
        payload_size=len(payload),
        format_name=ENCRYPTOR_LITE_FORMAT,
    )


def _read_secure_container(path: str | Path) -> tuple[Path, dict, bytes, bytes, bytes]:
    package_path, header, header_bytes, remainder = _read_prefixed_json_payload(path, ENCRYPTOR_SECURE_MAGIC)
    if header.get("format") != ENCRYPTOR_SECURE_FORMAT or header.get("version") != ENCRYPTOR_SECURE_VERSION:
        raise MediaPackageError("不支持的加密文件版本。")
    metadata_size = int(header.get("encrypted_metadata_size", -1))
    if metadata_size < 0 or len(remainder) < 4 + metadata_size:
        raise MediaPackageError("加密文件元数据不完整。")
    metadata_len = struct.unpack(">I", remainder[:4])[0]
    if metadata_len != metadata_size:
        raise MediaPackageError("加密文件元数据长度校验失败。")
    encrypted_metadata = remainder[4 : 4 + metadata_len]
    encrypted_file_data = remainder[4 + metadata_len :]
    return package_path, header, header_bytes, encrypted_metadata, encrypted_file_data


def _read_secure_package_info(path: str | Path) -> MediaPackageInfo:
    package_path, _, _, _, encrypted_file_data = _read_secure_container(path)
    return MediaPackageInfo(
        path=package_path,
        package_name=package_path.stem,
        created_at="",
        item_count=0,
        items=[],
        suffix=package_path.suffix,
        payload_size=len(encrypted_file_data),
        format_name=ENCRYPTOR_SECURE_FORMAT,
        requires_credentials=True,
    )


def _extract_lite_package(path: str | Path, output_dir: str | Path) -> list[Path]:
    header, encrypted_payload = _read_lite_container(path)
    try:
        plain = _decrypt_encryptor_payload(
            encrypted_payload,
            ENCRYPTOR_LITE_APP_KEY,
            _b64decode(header["payload_nonce"]),
            header.get("cipher", ENCRYPTOR_FILE_CIPHER_AES_GCM),
        )
    except Exception as exc:
        raise MediaPackageError("无法打开该轻量加密文件，文件可能不完整。") from exc
    if hashlib.sha256(plain).hexdigest() != header.get("original_sha256"):
        raise MediaPackageError("轻量加密文件校验失败，文件可能不完整。")

    target_dir = Path(output_dir).expanduser()
    target_dir.mkdir(parents=True, exist_ok=True)
    if header.get("payload_type") == ENCRYPTOR_LITE_PAYLOAD_BUNDLE:
        with tempfile.NamedTemporaryFile(prefix="maoqiu-lite-", suffix=".tar") as temp:
            temp.write(plain)
            temp.flush()
            _safe_extract_tar(Path(temp.name), target_dir)
        return _collect_playable_files(target_dir)

    filename = _safe_output_name(header.get("original_filename") or Path(path).stem)
    target = target_dir / filename
    target.write_bytes(plain)
    return [target] if classify_media_type(target) in {"video", "image"} else []


def _extract_secure_package(path: str | Path, output_dir: str | Path, username: str, password: str) -> list[Path]:
    if not username.strip() or not password:
        raise MediaPackageError("请输入用户名和密码。")
    package_path, header, header_bytes, encrypted_metadata, encrypted_file_data = _read_secure_container(path)
    try:
        metadata_key = _unwrap_secure_metadata_key(header, username, password)
        metadata = json.loads(
            _aesgcm_decrypt_with_aad(
                encrypted_metadata,
                metadata_key,
                _b64decode(header["metadata_nonce"]),
                header_bytes,
            ).decode("utf-8")
        )
    except Exception as exc:
        raise MediaPackageError("无法打开该加密文件，请检查用户名、密码或文件完整性。") from exc

    target_dir = Path(output_dir).expanduser()
    target_dir.mkdir(parents=True, exist_ok=True)
    try:
        plain = _decrypt_encryptor_payload(
            encrypted_file_data,
            _b64decode(metadata["file_key"]),
            _b64decode(metadata["file_nonce"]),
            metadata["file_cipher"],
        )
    except Exception as exc:
        raise MediaPackageError("无法解密文件内容，请检查文件完整性。") from exc
    if hashlib.sha256(plain).hexdigest() != metadata.get("original_sha256"):
        raise MediaPackageError("加密文件校验失败，文件可能不完整。")

    if metadata.get("payload_type") == ENCRYPTOR_LITE_PAYLOAD_BUNDLE:
        with tempfile.NamedTemporaryFile(prefix="maoqiu-secure-", suffix=".tar") as temp:
            temp.write(plain)
            temp.flush()
            _safe_extract_tar(Path(temp.name), target_dir)
        return _collect_playable_files(target_dir)

    filename = _safe_output_name(metadata.get("original_filename") or package_path.stem)
    target = target_dir / filename
    target.write_bytes(plain)
    return [target] if classify_media_type(target) in {"video", "image"} else []


def _unwrap_secure_metadata_key(header: dict, username: str, password: str) -> bytes:
    _ensure_argon2()
    for wrap in header.get("metadata_key_wraps", []):
        if wrap.get("type") != ENCRYPTOR_KEY_WRAP_PASSWORD:
            continue
        try:
            wrap_key = _derive_username_password_key(
                username,
                password,
                _b64decode(wrap["kdf_salt"]),
                memory_cost=int(header["kdf_memory_cost"]),
                time_cost=int(header["kdf_time_cost"]),
                parallelism=int(header["kdf_parallelism"]),
            )
            return _aesgcm_decrypt_with_aad(
                _b64decode(wrap["encrypted_key"]),
                wrap_key,
                _b64decode(wrap["nonce"]),
                _metadata_key_wrap_aad(wrap["id"], wrap["type"]),
            )
        except Exception:
            continue
    raise MediaPackageError("用户名或密码不正确。")


def _derive_username_password_key(
    username: str,
    password: str,
    salt: bytes,
    memory_cost: int,
    time_cost: int,
    parallelism: int,
) -> bytes:
    from argon2.low_level import Type, hash_secret_raw

    return hash_secret_raw(
        secret=f"{username.strip()}\n{password}".encode("utf-8"),
        salt=salt,
        time_cost=time_cost,
        memory_cost=memory_cost,
        parallelism=parallelism,
        hash_len=32,
        type=Type.ID,
    )


def _ensure_argon2() -> None:
    try:
        import argon2  # noqa: F401
    except ImportError as exc:
        raise MediaPackageError("缺少 argon2-cffi，无法打开 Maoqiu Encryptor 的用户名/密码加密文件。") from exc


def _decrypt_encryptor_payload(encrypted_data: bytes, key: bytes, nonce: bytes, cipher_name: str) -> bytes:
    if cipher_name in ENCRYPTOR_AES_GCM_FILE_CIPHERS:
        if len(encrypted_data) < GCM_TAG_LEN:
            raise MediaPackageError("加密内容不完整。")
        from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

        ciphertext = encrypted_data[:-GCM_TAG_LEN]
        tag = encrypted_data[-GCM_TAG_LEN:]
        decryptor = Cipher(algorithms.AES(key), modes.GCM(nonce, tag)).decryptor()
        return decryptor.update(ciphertext) + decryptor.finalize()
    if cipher_name == ENCRYPTOR_FILE_CIPHER_CHACHA20:
        from cryptography.hazmat.primitives.ciphers.aead import ChaCha20Poly1305

        return ChaCha20Poly1305(key).decrypt(nonce, encrypted_data, None)
    raise MediaPackageError("不支持的加密算法。")


def _aesgcm_decrypt_with_aad(payload: bytes, key: bytes, nonce: bytes, aad: bytes | None) -> bytes:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM

    return AESGCM(key).decrypt(nonce, payload, aad)


def _metadata_key_wrap_aad(wrap_id: str, wrap_type: str) -> bytes:
    return f"MAOQIU_METADATA_KEY_WRAP_V2|{wrap_type}|{wrap_id}".encode("utf-8")


def _b64decode(data: str) -> bytes:
    import base64

    return base64.b64decode(data.encode("ascii"))


def _safe_extract_tar(tar_path: Path, output_dir: Path) -> None:
    output_root = output_dir.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    with tarfile.open(tar_path, "r") as archive:
        for member in archive.getmembers():
            target = (output_root / member.name).resolve()
            if output_root != target and output_root not in target.parents:
                raise MediaPackageError("归档内容包含不安全路径，已停止解包。")
            if member.issym() or member.islnk():
                link_target = Path(member.linkname)
                resolved_link = link_target if link_target.is_absolute() else (target.parent / link_target)
                resolved_link = resolved_link.resolve()
                if output_root != resolved_link and output_root not in resolved_link.parents:
                    raise MediaPackageError("归档内容包含不安全链接，已停止解包。")
        archive.extractall(output_root)


def _collect_playable_files(root: Path) -> list[Path]:
    return [path for path in root.rglob("*") if path.is_file() and classify_media_type(path) in {"video", "image"}]


def _safe_output_name(name: str) -> str:
    candidate = Path(name).name.strip()
    return candidate or "restored-media"


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
