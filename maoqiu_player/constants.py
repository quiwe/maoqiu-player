from __future__ import annotations

from pathlib import Path

APP_ENGLISH_NAME = "MaoqiuPlayer"
APP_CHINESE_NAME = "毛球播放器"
APP_DISPLAY_NAME = f"{APP_CHINESE_NAME} / {APP_ENGLISH_NAME}"
APP_VERSION = "0.1.13"
APP_AUTHOR = "Maoqiu"
REPOSITORY_NAME = "maoqiu-player"

DEFAULT_MEDIA_PACKAGE_SUFFIX = ".mqp"
MEDIA_PACKAGE_MAGIC = b"MAOQIU_PLAYER_ENC_V1"
MEDIA_PACKAGE_FORMAT = "MaoqiuPlayerMediaPackage"
MEDIA_PACKAGE_VERSION = 1

APP_DATA_DIR = Path.home() / ".maoqiu-player"
LIBRARY_DB_PATH = APP_DATA_DIR / "library.json"
CACHE_DIR = APP_DATA_DIR / "cache"
PACKAGE_CACHE_DIR = CACHE_DIR / "media-packages"

SUPPORTED_VIDEO_EXTENSIONS = {
    ".mp4",
    ".m4v",
    ".mov",
    ".mkv",
    ".avi",
    ".webm",
    ".wmv",
    ".flv",
}

SUPPORTED_IMAGE_EXTENSIONS = {
    ".jpg",
    ".jpeg",
    ".png",
    ".gif",
    ".bmp",
    ".webp",
    ".tif",
    ".tiff",
}

SUPPORTED_MEDIA_EXTENSIONS = SUPPORTED_VIDEO_EXTENSIONS | SUPPORTED_IMAGE_EXTENSIONS | {DEFAULT_MEDIA_PACKAGE_SUFFIX}
