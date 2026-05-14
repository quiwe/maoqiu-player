from __future__ import annotations

import json
import platform
import re
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path

from PySide6.QtCore import QThread, Signal

from .constants import APP_VERSION, UPDATE_REPOSITORY

GITHUB_API_RELEASE_URL = f"https://api.github.com/repos/{UPDATE_REPOSITORY}/releases/latest"
USER_AGENT = f"MaoqiuPlayer/{APP_VERSION}"
DOWNLOAD_CHUNK_SIZE = 1024 * 128


@dataclass(frozen=True, slots=True)
class ReleaseAsset:
    name: str
    download_url: str
    size: int = 0


@dataclass(frozen=True, slots=True)
class UpdateInfo:
    version: str
    tag_name: str
    release_name: str
    release_url: str
    notes: str
    asset: ReleaseAsset | None


def fetch_latest_release() -> dict:
    request = urllib.request.Request(GITHUB_API_RELEASE_URL, headers={"User-Agent": USER_AGENT, "Accept": "application/vnd.github+json"})
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.loads(response.read().decode("utf-8"))


def parse_update_info(release: dict, system_name: str | None = None) -> UpdateInfo | None:
    tag_name = str(release.get("tag_name") or "").strip()
    latest_version = normalize_version(tag_name)
    if not latest_version or not is_newer_version(latest_version, APP_VERSION):
        return None

    assets = [
        ReleaseAsset(
            name=str(asset.get("name") or ""),
            download_url=str(asset.get("browser_download_url") or ""),
            size=int(asset.get("size") or 0),
        )
        for asset in release.get("assets", [])
        if asset.get("name") and asset.get("browser_download_url")
    ]
    return UpdateInfo(
        version=latest_version,
        tag_name=tag_name,
        release_name=str(release.get("name") or tag_name),
        release_url=str(release.get("html_url") or ""),
        notes=str(release.get("body") or ""),
        asset=select_platform_asset(assets, system_name=system_name),
    )


def normalize_version(value: str) -> str:
    match = re.search(r"\d+(?:\.\d+){1,3}", value)
    return match.group(0) if match else ""


def is_newer_version(candidate: str, current: str) -> bool:
    candidate_parts = _version_parts(candidate)
    current_parts = _version_parts(current)
    length = max(len(candidate_parts), len(current_parts))
    candidate_parts.extend([0] * (length - len(candidate_parts)))
    current_parts.extend([0] * (length - len(current_parts)))
    return candidate_parts > current_parts


def select_platform_asset(assets: list[ReleaseAsset], system_name: str | None = None) -> ReleaseAsset | None:
    system = (system_name or platform.system()).lower()
    if system == "darwin":
        patterns = ("macos", ".dmg")
    elif system == "windows":
        patterns = ("windows", ".exe")
    elif system == "linux":
        patterns = (".deb",)
    else:
        patterns = ()

    if patterns:
        for asset in assets:
            name = asset.name.lower()
            if all(pattern in name for pattern in patterns):
                return asset
    return None


def default_download_path(asset_name: str) -> Path:
    downloads = Path.home() / "Downloads"
    target_dir = downloads if downloads.exists() else Path.home()
    safe_name = Path(asset_name).name or "MaoqiuPlayer-update"
    return target_dir / safe_name


class UpdateCheckWorker(QThread):
    update_found = Signal(object)
    no_update = Signal()
    failed = Signal(str)

    def run(self) -> None:
        try:
            update = parse_update_info(fetch_latest_release())
        except (OSError, urllib.error.URLError, json.JSONDecodeError) as exc:
            self.failed.emit(str(exc))
            return
        if update is None:
            self.no_update.emit()
        else:
            self.update_found.emit(update)


class InstallerDownloadWorker(QThread):
    progress_changed = Signal(int, int)
    finished_download = Signal(object)
    failed = Signal(str)

    def __init__(self, asset: ReleaseAsset, output_path: Path) -> None:
        super().__init__()
        self.asset = asset
        self.output_path = output_path
        self._cancelled = False

    def cancel(self) -> None:
        self._cancelled = True

    def run(self) -> None:
        request = urllib.request.Request(self.asset.download_url, headers={"User-Agent": USER_AGENT})
        temp_path = self.output_path.with_name(f".{self.output_path.name}.download")
        try:
            self.output_path.parent.mkdir(parents=True, exist_ok=True)
            with urllib.request.urlopen(request, timeout=30) as response, temp_path.open("wb") as handle:
                total = int(response.headers.get("Content-Length") or self.asset.size or 0)
                downloaded = 0
                while True:
                    chunk = response.read(DOWNLOAD_CHUNK_SIZE)
                    if not chunk:
                        break
                    if self._cancelled:
                        raise RuntimeError("下载已取消。")
                    handle.write(chunk)
                    downloaded += len(chunk)
                    self.progress_changed.emit(downloaded, total)
            temp_path.replace(self.output_path)
        except Exception as exc:
            temp_path.unlink(missing_ok=True)
            self.failed.emit(str(exc))
            return
        self.finished_download.emit(self.output_path)


def _version_parts(value: str) -> list[int]:
    version = normalize_version(value)
    if not version:
        return [0]
    return [int(part) for part in version.split(".")]
