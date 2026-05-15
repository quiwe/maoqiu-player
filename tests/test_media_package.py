import io
import json
import os
import struct
import tarfile
from pathlib import Path

import pytest

pytest.importorskip("cryptography")

from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from maoqiu_player.media_package import (
    ENCRYPTOR_FILE_CIPHER_AES_GCM,
    ENCRYPTOR_LITE_APP_KEY,
    ENCRYPTOR_LITE_FORMAT,
    ENCRYPTOR_LITE_MAGIC,
    ENCRYPTOR_LITE_PAYLOAD_BUNDLE,
    ENCRYPTOR_LITE_PAYLOAD_FILE,
    ENCRYPTOR_LITE_VERSION,
    ENCRYPTOR_SECURE_MAGIC,
    create_media_package,
    extract_media_package,
    is_media_package,
    media_package_requires_credentials,
    read_media_package_info,
    verify_media_package,
)


def test_media_package_roundtrip(tmp_path) -> None:
    source = tmp_path / "sample.jpg"
    source.write_bytes(b"fake image bytes")
    output = tmp_path / "album.mqp"

    info = create_media_package([source], output, package_name="album")

    assert output.exists()
    assert is_media_package(output)
    assert verify_media_package(output)
    assert info.package_name == "album"
    assert info.item_count == 1
    assert info.items[0]["name"] == "sample.jpg"

    extracted = extract_media_package(output, tmp_path / "cache")

    assert len(extracted) == 1
    assert extracted[0].read_bytes() == b"fake image bytes"


def test_media_package_roundtrip_extracts_multiple_media(tmp_path) -> None:
    photo = tmp_path / "photo.jpg"
    video = tmp_path / "clip.mp4"
    photo.write_bytes(b"fake image bytes")
    video.write_bytes(b"fake video bytes")
    output = tmp_path / "album.mqp"

    info = create_media_package([photo, video], output, package_name="album")

    assert info.item_count == 2
    assert [item["name"] for item in info.items] == ["photo.jpg", "clip.mp4"]

    extracted = extract_media_package(output, tmp_path / "cache")

    assert [path.name for path in extracted] == ["photo.jpg", "clip.mp4"]
    assert [path.read_bytes() for path in extracted] == [b"fake image bytes", b"fake video bytes"]


def test_encryptor_lite_single_media_file_roundtrip(tmp_path) -> None:
    packed = tmp_path / "photo.anything"
    _write_lite_container(packed, "photo.jpg", b"fake encrypted photo", ENCRYPTOR_LITE_PAYLOAD_FILE)

    assert is_media_package(packed)
    assert verify_media_package(packed)

    info = read_media_package_info(packed)
    assert info.format_name == ENCRYPTOR_LITE_FORMAT
    assert info.items[0]["name"] == "photo.jpg"

    extracted = extract_media_package(packed, tmp_path / "cache")

    assert len(extracted) == 1
    assert extracted[0].name == "photo.jpg"
    assert extracted[0].read_bytes() == b"fake encrypted photo"


def test_encryptor_lite_bundle_extracts_playable_files(tmp_path) -> None:
    tar_bytes = io.BytesIO()
    with tarfile.open(fileobj=tar_bytes, mode="w") as archive:
        data = b"fake video"
        info = tarfile.TarInfo("album/clip.mp4")
        info.size = len(data)
        archive.addfile(info, io.BytesIO(data))

        data = b"notes"
        info = tarfile.TarInfo("album/readme.txt")
        info.size = len(data)
        archive.addfile(info, io.BytesIO(data))

    packed = tmp_path / "album.bin"
    _write_lite_container(
        packed,
        "lite-bundle.tar",
        tar_bytes.getvalue(),
        ENCRYPTOR_LITE_PAYLOAD_BUNDLE,
        extra_header={"bundle_items": ["album"]},
    )

    extracted = extract_media_package(packed, tmp_path / "cache")

    assert [path.name for path in extracted] == ["clip.mp4"]
    assert extracted[0].read_bytes() == b"fake video"


def test_encryptor_secure_file_roundtrip(tmp_path) -> None:
    pytest.importorskip("argon2")
    packed = tmp_path / "secure.anysuffix"
    _write_secure_container(packed, "secret.jpg", b"secure image bytes", username="user", password="pass")

    assert is_media_package(packed)
    assert media_package_requires_credentials(packed)

    info = read_media_package_info(packed)
    assert info.format_name == "MAOQIU"
    assert info.requires_credentials

    extracted = extract_media_package(packed, tmp_path / "cache", username="user", password="pass")

    assert len(extracted) == 1
    assert extracted[0].name == "secret.jpg"
    assert extracted[0].read_bytes() == b"secure image bytes"


def _write_lite_container(
    output: Path,
    original_filename: str,
    payload: bytes,
    payload_type: str,
    extra_header: dict | None = None,
) -> None:
    import hashlib
    import os

    nonce = os.urandom(12)
    header = {
        "format": ENCRYPTOR_LITE_FORMAT,
        "version": ENCRYPTOR_LITE_VERSION,
        "payload_type": payload_type,
        "original_filename": original_filename,
        "original_size": len(payload),
        "original_sha256": hashlib.sha256(payload).hexdigest(),
        "payload_nonce": _b64encode(nonce),
        "cipher": ENCRYPTOR_FILE_CIPHER_AES_GCM,
    }
    if extra_header:
        header.update(extra_header)

    header_bytes = json.dumps(header, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode("utf-8")
    encrypted = _encrypt_aes_gcm_file_payload(payload, ENCRYPTOR_LITE_APP_KEY, nonce)
    output.write_bytes(ENCRYPTOR_LITE_MAGIC + struct.pack(">I", len(header_bytes)) + header_bytes + encrypted)


def _write_secure_container(output: Path, original_filename: str, payload: bytes, username: str, password: str) -> None:
    import hashlib

    from argon2.low_level import Type, hash_secret_raw

    metadata_key = os.urandom(32)
    metadata_nonce = os.urandom(12)
    file_key = os.urandom(32)
    file_nonce = os.urandom(12)
    wrap_salt = os.urandom(16)
    wrap_nonce = os.urandom(12)

    metadata = {
        "original_filename": original_filename,
        "original_extension": Path(original_filename).suffix,
        "original_size": len(payload),
        "original_sha256": hashlib.sha256(payload).hexdigest(),
        "file_cipher": ENCRYPTOR_FILE_CIPHER_AES_GCM,
        "file_key": _b64encode(file_key),
        "file_nonce": _b64encode(file_nonce),
    }
    metadata_plaintext = json.dumps(metadata, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode("utf-8")
    wrap_key = hash_secret_raw(
        secret=f"{username.strip()}\n{password}".encode("utf-8"),
        salt=wrap_salt,
        time_cost=1,
        memory_cost=1024,
        parallelism=1,
        hash_len=32,
        type=Type.ID,
    )
    encrypted_metadata_size = len(metadata_plaintext) + 16
    password_wrap = {
        "type": "password",
        "id": "password",
        "nonce": _b64encode(wrap_nonce),
        "kdf_salt": _b64encode(wrap_salt),
        "username_hint": username,
    }
    password_wrap["encrypted_key"] = _b64encode(
        AESGCM(wrap_key).encrypt(wrap_nonce, metadata_key, b"MAOQIU_METADATA_KEY_WRAP_V2|password|password")
    )
    header = {
        "format": "MAOQIU",
        "version": 1,
        "auth_mode": "username_password",
        "metadata_cipher": "AES-256-GCM",
        "kdf": "Argon2id",
        "kdf_salt": _b64encode(wrap_salt),
        "kdf_memory_cost": 1024,
        "kdf_time_cost": 1,
        "kdf_parallelism": 1,
        "metadata_nonce": _b64encode(metadata_nonce),
        "username_hint": username,
        "metadata_key_wraps": [password_wrap],
        "encrypted_metadata_size": encrypted_metadata_size,
    }
    header_bytes = json.dumps(header, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode("utf-8")
    encrypted_metadata = AESGCM(metadata_key).encrypt(metadata_nonce, metadata_plaintext, header_bytes)
    encrypted_payload = AESGCM(file_key).encrypt(file_nonce, payload, None)
    output.write_bytes(
        ENCRYPTOR_SECURE_MAGIC
        + struct.pack(">I", len(header_bytes))
        + header_bytes
        + struct.pack(">I", len(encrypted_metadata))
        + encrypted_metadata
        + encrypted_payload
    )


def _encrypt_aes_gcm_file_payload(payload: bytes, key: bytes, nonce: bytes) -> bytes:
    encryptor = Cipher(algorithms.AES(key), modes.GCM(nonce)).encryptor()
    ciphertext = encryptor.update(payload) + encryptor.finalize()
    return ciphertext + encryptor.tag


def _b64encode(data: bytes) -> str:
    import base64

    return base64.b64encode(data).decode("ascii")
