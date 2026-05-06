from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path

from .constants import LIBRARY_DB_PATH, SUPPORTED_IMAGE_EXTENSIONS, SUPPORTED_VIDEO_EXTENSIONS
from .models import MediaItem, classify_media_type


class MediaLibrary:
    def __init__(self, db_path: Path = LIBRARY_DB_PATH) -> None:
        self.db_path = db_path
        self.items: dict[str, MediaItem] = {}
        self.load()

    def load(self) -> None:
        self.items.clear()
        if not self.db_path.exists():
            return
        try:
            records = json.loads(self.db_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return
        for record in records:
            path = Path(record.get("path", ""))
            if not path.exists() or classify_media_type(path) == "unknown":
                continue
            last_played = _parse_datetime(record.get("last_played_at"))
            try:
                self.items[str(path)] = MediaItem.from_path(path, last_played_at=last_played)
            except OSError:
                continue

    def save(self) -> None:
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        records = [item.to_record() for item in self.items.values()]
        self.db_path.write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding="utf-8")

    def import_paths(self, paths: list[str | Path]) -> list[MediaItem]:
        imported: list[MediaItem] = []
        for raw_path in paths:
            path = Path(raw_path).expanduser()
            if not path.exists() or not path.is_file():
                continue
            if path.suffix.lower() not in SUPPORTED_VIDEO_EXTENSIONS | SUPPORTED_IMAGE_EXTENSIONS:
                continue
            try:
                item = MediaItem.from_path(path)
            except OSError:
                continue
            self.items[str(path)] = item
            imported.append(item)
        if imported:
            self.save()
        return imported

    def add_recent(self, path: str | Path) -> MediaItem | None:
        media_path = Path(path).expanduser()
        if not media_path.exists() or classify_media_type(media_path) not in {"video", "image"}:
            return None
        item = self.items.get(str(media_path))
        if item is None:
            item = MediaItem.from_path(media_path)
            self.items[str(media_path)] = item
        item.last_played_at = datetime.now()
        self.save()
        return item

    def all_items(self) -> list[MediaItem]:
        return list(self.items.values())

    def recent_items(self, limit: int | None = None) -> list[MediaItem]:
        items = [item for item in self.items.values() if item.last_played_at is not None]
        items.sort(key=lambda item: item.last_played_at or datetime.min, reverse=True)
        return items[:limit] if limit else items

    def filtered_items(self, category: str = "all", search_text: str = "", sort_by: str = "modified") -> list[MediaItem]:
        items = self.recent_items() if category == "recent" else self.all_items()
        if category in {"video", "image"}:
            items = [item for item in items if item.media_type == category]
        query = search_text.strip().lower()
        if query:
            items = [
                item
                for item in items
                if query in item.title.lower() or query in item.path.name.lower() or query in item.extension.lower()
            ]
        if sort_by == "name":
            items.sort(key=lambda item: item.title.lower())
        elif sort_by == "format":
            items.sort(key=lambda item: (item.extension, item.title.lower()))
        elif sort_by == "recent":
            items.sort(key=lambda item: item.last_played_at or datetime.min, reverse=True)
        else:
            items.sort(key=lambda item: item.modified_at, reverse=True)
        return items

    def stats(self) -> dict[str, int]:
        all_items = self.all_items()
        return {
            "total": len(all_items),
            "videos": sum(1 for item in all_items if item.media_type == "video"),
            "images": sum(1 for item in all_items if item.media_type == "image"),
            "recent": len(self.recent_items()),
        }


def _parse_datetime(value: str | None) -> datetime | None:
    if not value:
        return None
    try:
        return datetime.fromisoformat(value)
    except ValueError:
        return None
