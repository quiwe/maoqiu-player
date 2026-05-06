import pytest

pytest.importorskip("cryptography")

from maoqiu_player.media_package import create_media_package, extract_media_package, is_media_package, verify_media_package


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
