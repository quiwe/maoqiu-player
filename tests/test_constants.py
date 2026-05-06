from maoqiu_player.constants import (
    APP_CHINESE_NAME,
    APP_ENGLISH_NAME,
    DEFAULT_MEDIA_PACKAGE_SUFFIX,
    MEDIA_PACKAGE_MAGIC,
    REPOSITORY_NAME,
)


def test_project_identity_constants() -> None:
    assert APP_ENGLISH_NAME == "MaoqiuPlayer"
    assert APP_CHINESE_NAME == "毛球播放器"
    assert REPOSITORY_NAME == "maoqiu-player"
    assert DEFAULT_MEDIA_PACKAGE_SUFFIX == ".mqp"
    assert MEDIA_PACKAGE_MAGIC == b"MAOQIU_PLAYER_ENC_V1"
