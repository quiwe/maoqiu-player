from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

from .constants import DEFAULT_MEDIA_PACKAGE_SUFFIX, SUPPORTED_IMAGE_EXTENSIONS, SUPPORTED_VIDEO_EXTENSIONS


@dataclass(slots=True)
class MediaItem:
    path: Path
    title: str
    media_type: str
    extension: str
    size: int
    modified_at: datetime
    last_played_at: datetime | None = None

    @classmethod
    def from_path(cls, path: str | Path, last_played_at: datetime | None = None) -> "MediaItem":
        file_path = Path(path).expanduser()
        stat = file_path.stat()
        suffix = file_path.suffix.lower()
        return cls(
            path=file_path,
            title=file_path.stem,
            media_type=classify_media_type(file_path),
            extension=suffix,
            size=stat.st_size,
            modified_at=datetime.fromtimestamp(stat.st_mtime),
            last_played_at=last_played_at,
        )

    def to_record(self) -> dict:
        return {
            "path": str(self.path),
            "last_played_at": self.last_played_at.isoformat() if self.last_played_at else None,
        }


def classify_media_type(path: str | Path) -> str:
    suffix = Path(path).suffix.lower()
    if suffix in SUPPORTED_VIDEO_EXTENSIONS:
        return "video"
    if suffix in SUPPORTED_IMAGE_EXTENSIONS:
        return "image"
    if suffix == DEFAULT_MEDIA_PACKAGE_SUFFIX:
        return "package"
    return "unknown"


def format_media_type(media_type: str) -> str:
    names = {
        "video": "视频",
        "image": "图片",
        "package": "媒体包",
        "unknown": "文件",
    }
    return names.get(media_type, "文件")
