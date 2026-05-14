from maoqiu_player.updater import ReleaseAsset, is_newer_version, parse_update_info, select_platform_asset


def test_version_comparison() -> None:
    assert is_newer_version("0.1.18", "0.1.17")
    assert is_newer_version("v0.2.0", "0.1.17")
    assert not is_newer_version("0.1.17", "0.1.17")
    assert not is_newer_version("0.1.16", "0.1.17")


def test_select_platform_asset() -> None:
    assets = [
        ReleaseAsset("MaoqiuPlayer-macOS.dmg", "https://example.test/mac"),
        ReleaseAsset("MaoqiuPlayer-Windows-Setup.exe", "https://example.test/win"),
        ReleaseAsset("maoqiu-player_0.1.17_amd64.deb", "https://example.test/linux"),
    ]

    assert select_platform_asset(assets, "Darwin") == assets[0]
    assert select_platform_asset(assets, "Windows") == assets[1]
    assert select_platform_asset(assets, "Linux") == assets[2]


def test_parse_update_info_uses_matching_asset() -> None:
    release = {
        "tag_name": "v0.1.18",
        "name": "MaoqiuPlayer v0.1.18",
        "html_url": "https://github.com/quiwe/maoqiu-player/releases/tag/v0.1.18",
        "body": "- update",
        "assets": [
            {"name": "MaoqiuPlayer-Windows-Setup.exe", "browser_download_url": "https://example.test/win", "size": 123},
        ],
    }

    update = parse_update_info(release, system_name="Windows")

    assert update is not None
    assert update.version == "0.1.18"
    assert update.asset is not None
    assert update.asset.name == "MaoqiuPlayer-Windows-Setup.exe"
