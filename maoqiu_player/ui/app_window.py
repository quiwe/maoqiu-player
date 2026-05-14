from __future__ import annotations

import time
from pathlib import Path

from PySide6.QtCore import Qt, QTimer, QUrl
from PySide6.QtGui import QDesktopServices
from PySide6.QtWidgets import (
    QApplication,
    QFileDialog,
    QFrame,
    QHBoxLayout,
    QInputDialog,
    QLabel,
    QLineEdit,
    QMainWindow,
    QMessageBox,
    QProgressDialog,
    QPushButton,
    QStackedWidget,
    QVBoxLayout,
    QWidget,
)

from maoqiu_player.constants import (
    APP_CHINESE_NAME,
    APP_DISPLAY_NAME,
    APP_VERSION,
    DEFAULT_MEDIA_PACKAGE_SUFFIX,
    PACKAGE_CACHE_DIR,
    SUPPORTED_IMAGE_EXTENSIONS,
    SUPPORTED_MEDIA_EXTENSIONS,
    SUPPORTED_VIDEO_EXTENSIONS,
)
from maoqiu_player.media_library import MediaLibrary
from maoqiu_player.media_package import (
    MediaPackageError,
    extract_media_package,
    is_media_package,
    media_package_requires_credentials,
)
from maoqiu_player.models import classify_media_type
from maoqiu_player.updater import InstallerDownloadWorker, UpdateCheckWorker, UpdateInfo, default_download_path

from .advanced_tools_page import AdvancedToolsPage
from .home_page import HomePage
from .image_viewer_page import ImageViewerPage
from .library_page import LibraryPage
from .media_package_page import MediaPackagePage
from .playlist_page import PlaylistPage
from .settings_page import SettingsPage
from .style import apply_theme
from .video_player_page import VideoPlayerPage


class MainWindow(QMainWindow):
    def __init__(self, app: QApplication) -> None:
        super().__init__()
        self.app = app
        self.library = MediaLibrary()
        self.setObjectName("mainWindow")
        self.setWindowTitle(APP_DISPLAY_NAME)
        self.resize(1240, 820)

        self.sidebar_buttons: dict[str, QPushButton] = {}
        self.stack = QStackedWidget()
        self.search = QLineEdit()
        self.update_worker: UpdateCheckWorker | None = None
        self.download_worker: InstallerDownloadWorker | None = None
        self.download_progress: QProgressDialog | None = None

        self.home_page = HomePage()
        self.library_page = LibraryPage(self.library)
        self.playlist_page = PlaylistPage()
        self.video_page = VideoPlayerPage()
        self.image_page = ImageViewerPage()
        self.settings_page = SettingsPage()
        self.advanced_tools_page = AdvancedToolsPage()
        self.media_package_page = MediaPackagePage()

        self._build()
        self._wire()
        self.refresh_home()
        self.navigate("home")
        QTimer.singleShot(1200, self.check_for_updates)

    def _build(self) -> None:
        root = QWidget()
        shell = QHBoxLayout(root)
        shell.setContentsMargins(0, 0, 0, 0)
        shell.setSpacing(0)

        sidebar = QFrame()
        sidebar.setObjectName("sidebar")
        sidebar.setFixedWidth(218)
        sidebar_layout = QVBoxLayout(sidebar)
        sidebar_layout.setContentsMargins(18, 20, 18, 20)
        sidebar_layout.setSpacing(8)

        brand = QLabel(APP_CHINESE_NAME)
        brand.setObjectName("brandTitle")
        english = QLabel("MaoqiuPlayer")
        english.setObjectName("mutedText")
        sidebar_layout.addWidget(brand)
        sidebar_layout.addWidget(english)
        sidebar_layout.addSpacing(18)

        nav_items = [
            ("home", "首页"),
            ("recent", "最近播放"),
            ("video", "本地视频"),
            ("image", "本地图片"),
            ("playlist", "播放列表"),
            ("settings", "设置"),
        ]
        for key, label in nav_items:
            button = QPushButton(label)
            button.setObjectName("navButton")
            button.setCheckable(True)
            button.clicked.connect(lambda checked=False, name=key: self.navigate(name))
            self.sidebar_buttons[key] = button
            sidebar_layout.addWidget(button)
        sidebar_layout.addStretch(1)

        content = QVBoxLayout()
        content.setContentsMargins(0, 0, 0, 0)
        content.setSpacing(0)
        top_bar = QFrame()
        top_bar.setObjectName("topBar")
        top_layout = QHBoxLayout(top_bar)
        top_layout.setContentsMargins(22, 14, 22, 14)
        self.search.setObjectName("searchField")
        self.search.setPlaceholderText("搜索媒体库")
        open_button = QPushButton("打开文件")
        open_button.setObjectName("primaryButton")
        open_button.clicked.connect(self.open_file_dialog)
        top_layout.addWidget(self.search, 1)
        top_layout.addWidget(open_button)
        content.addWidget(top_bar)

        for page in (
            self.home_page,
            self.library_page,
            self.playlist_page,
            self.video_page,
            self.image_page,
            self.settings_page,
            self.advanced_tools_page,
            self.media_package_page,
        ):
            self.stack.addWidget(page)
        content.addWidget(self.stack, 1)

        shell.addWidget(sidebar)
        content_widget = QWidget()
        content_widget.setLayout(content)
        shell.addWidget(content_widget, 1)
        self.setCentralWidget(root)

    def _wire(self) -> None:
        self.search.returnPressed.connect(self.search_library)
        self.home_page.open_file_requested.connect(self.open_file_dialog)
        self.home_page.navigate_requested.connect(self.navigate)
        self.home_page.open_media_requested.connect(self.open_media)
        self.library_page.open_media_requested.connect(self.open_media)
        self.library_page.library_changed.connect(self.refresh_home)
        self.video_page.back_requested.connect(lambda: self.navigate("recent"))
        self.image_page.back_requested.connect(lambda: self.navigate("recent"))
        self.settings_page.theme_changed.connect(lambda theme: apply_theme(self.app, theme))
        self.settings_page.advanced_tools_requested.connect(lambda: self.navigate("advanced"))
        self.advanced_tools_page.back_requested.connect(lambda: self.navigate("settings"))
        self.advanced_tools_page.media_package_requested.connect(lambda: self.navigate("packages"))
        self.media_package_page.back_requested.connect(lambda: self.navigate("advanced"))

    def navigate(self, target: str) -> None:
        self.video_page.stop()
        if target == "home":
            self.stack.setCurrentWidget(self.home_page)
        elif target in {"recent", "video", "image"}:
            self.library_page.set_category(target)
            self.stack.setCurrentWidget(self.library_page)
        elif target == "playlist":
            self.stack.setCurrentWidget(self.playlist_page)
        elif target == "settings":
            self.stack.setCurrentWidget(self.settings_page)
        elif target == "advanced":
            self.stack.setCurrentWidget(self.advanced_tools_page)
        elif target == "packages":
            self.stack.setCurrentWidget(self.media_package_page)
        else:
            self.stack.setCurrentWidget(self.home_page)
            target = "home"
        self._set_checked_nav(target)
        self.refresh_home()

    def open_file_dialog(self) -> None:
        suffixes = " ".join(f"*{suffix}" for suffix in sorted(SUPPORTED_MEDIA_EXTENSIONS))
        path, _ = QFileDialog.getOpenFileName(self, "打开媒体", "", f"媒体文件 ({suffixes});;所有文件 (*)")
        if path:
            self.open_media(path)

    def open_media(self, path: str) -> None:
        media_path = Path(path)
        if not media_path.exists():
            QMessageBox.warning(self, "打开媒体", "文件不存在。")
            return
        if media_path.suffix.lower() == DEFAULT_MEDIA_PACKAGE_SUFFIX or is_media_package(media_path):
            self._open_media_package(media_path)
            return

        media_type = classify_media_type(media_path)
        if media_type == "video":
            self.library.add_recent(media_path)
            self.library_page.refresh()
            self.refresh_home()
            self.stack.setCurrentWidget(self.video_page)
            self._set_checked_nav("")
            self.video_page.open_video(media_path)
            return
        if media_type == "image":
            self.library.add_recent(media_path)
            self.library_page.refresh()
            self.refresh_home()
            playlist = [item.path for item in self.library.filtered_items("image", "", "name")]
            self.stack.setCurrentWidget(self.image_page)
            self._set_checked_nav("")
            self.image_page.open_image(media_path, playlist)
            return
        QMessageBox.information(self, "打开媒体", "暂不支持该文件格式。")

    def search_library(self) -> None:
        self.library_page.search.setText(self.search.text())
        self.library_page.set_category("all")
        self.stack.setCurrentWidget(self.library_page)
        self._set_checked_nav("")

    def refresh_home(self) -> None:
        self.home_page.update_overview(self.library.stats(), self.library.recent_items(limit=5))

    def check_for_updates(self) -> None:
        if self.update_worker is not None:
            return
        self.update_worker = UpdateCheckWorker()
        self.update_worker.update_found.connect(self._handle_update_found)
        self.update_worker.failed.connect(lambda _message: None)
        self.update_worker.finished.connect(self.update_worker.deleteLater)
        self.update_worker.finished.connect(lambda: setattr(self, "update_worker", None))
        self.update_worker.start()

    def _handle_update_found(self, update: UpdateInfo) -> None:
        if update.asset is None:
            response = QMessageBox.question(
                self,
                "发现新版本",
                f"发现新版本 {update.tag_name}，但没有找到当前平台的安装包。\n是否打开 GitHub 发布页面？",
            )
            if response == QMessageBox.StandardButton.Yes and update.release_url:
                QDesktopServices.openUrl(QUrl(update.release_url))
            return

        message = (
            f"发现新版本 {update.tag_name}。\n\n"
            f"当前版本：{APP_VERSION}\n"
            f"安装包：{update.asset.name}\n\n"
            "是否现在下载安装包？"
        )
        response = QMessageBox.question(self, "发现新版本", message)
        if response == QMessageBox.StandardButton.Yes:
            self._download_update(update)

    def _download_update(self, update: UpdateInfo) -> None:
        if update.asset is None or self.download_worker is not None:
            return
        output_path = default_download_path(update.asset.name)
        self.download_progress = QProgressDialog("正在下载安装包...", "取消", 0, 100, self)
        self.download_progress.setWindowTitle("下载更新")
        self.download_progress.setWindowModality(Qt.WindowModality.WindowModal)
        self.download_progress.setMinimumDuration(0)
        self.download_progress.setValue(0)

        self.download_worker = InstallerDownloadWorker(update.asset, output_path)
        self.download_worker.progress_changed.connect(self._handle_download_progress)
        self.download_worker.finished_download.connect(self._handle_download_finished)
        self.download_worker.failed.connect(self._handle_download_failed)
        self.download_worker.finished.connect(self.download_worker.deleteLater)
        self.download_worker.finished.connect(lambda: setattr(self, "download_worker", None))
        self.download_progress.canceled.connect(self.download_worker.cancel)
        self.download_worker.start()

    def _handle_download_progress(self, downloaded: int, total: int) -> None:
        if self.download_progress is None:
            return
        if total > 0:
            percent = min(100, int(downloaded * 100 / total))
            self.download_progress.setRange(0, 100)
            self.download_progress.setValue(percent)
            self.download_progress.setLabelText(f"正在下载安装包... {percent}%")
        else:
            self.download_progress.setRange(0, 0)
            self.download_progress.setLabelText("正在下载安装包...")

    def _handle_download_finished(self, path: Path) -> None:
        if self.download_progress is not None:
            self.download_progress.close()
            self.download_progress = None
        response = QMessageBox.question(
            self,
            "下载完成",
            f"安装包已下载到：\n{path}\n\n是否现在打开安装包？",
        )
        if response == QMessageBox.StandardButton.Yes:
            QDesktopServices.openUrl(QUrl.fromLocalFile(str(path)))

    def _handle_download_failed(self, message: str) -> None:
        if self.download_progress is not None:
            self.download_progress.close()
            self.download_progress = None
        QMessageBox.warning(self, "下载失败", message or "下载安装包失败，请稍后重试。")

    def _open_media_package(self, package_path: Path) -> None:
        cache_dir = PACKAGE_CACHE_DIR / f"{package_path.stem}-{int(time.time())}"
        if media_package_requires_credentials(package_path):
            username, ok = QInputDialog.getText(self, "打开加密文件", "用户名")
            if not ok:
                return
            password, ok = QInputDialog.getText(self, "打开加密文件", "密码", QLineEdit.EchoMode.Password)
            if not ok:
                return
            try:
                extracted = extract_media_package(package_path, cache_dir, username=username, password=password)
            except MediaPackageError as exc:
                QMessageBox.warning(self, "打开加密文件", str(exc))
                return
            self._open_extracted_media(extracted)
            return

        try:
            extracted = extract_media_package(package_path, cache_dir)
        except MediaPackageError:
            phrase, ok = QInputDialog.getText(self, "打开媒体包", "访问口令（如有）", QLineEdit.EchoMode.Password)
            if not ok:
                return
            try:
                extracted = extract_media_package(package_path, cache_dir, access_phrase=phrase)
            except MediaPackageError as exc:
                QMessageBox.warning(self, "打开媒体包", str(exc))
                return

        self._open_extracted_media(extracted)

    def _open_extracted_media(self, extracted: list[Path]) -> None:
        imported = self.library.import_paths(extracted)
        self.library_page.refresh()
        self.refresh_home()
        first = next((item for item in imported if item.media_type in {"video", "image"}), None)
        if first is None:
            QMessageBox.information(self, "打开媒体包", "媒体包中没有可播放的项目。")
            return
        self.open_media(str(first.path))

    def _set_checked_nav(self, active: str) -> None:
        if active in {"advanced", "packages"}:
            active = "settings"
        for key, button in self.sidebar_buttons.items():
            button.setChecked(key == active)
