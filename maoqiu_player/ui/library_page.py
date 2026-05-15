from __future__ import annotations

from pathlib import Path

from PySide6.QtCore import QSize, Qt, Signal
from PySide6.QtWidgets import (
    QComboBox,
    QFileDialog,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QPushButton,
    QVBoxLayout,
    QWidget,
)

from maoqiu_player.constants import SUPPORTED_IMAGE_EXTENSIONS, SUPPORTED_VIDEO_EXTENSIONS
from maoqiu_player.media_library import MediaLibrary
from maoqiu_player.models import MediaItem

from .widgets import format_bytes, media_thumbnail


class LibraryPage(QWidget):
    open_media_requested = Signal(str)
    library_changed = Signal()

    CATEGORY_MAP = {
        "全部媒体": "all",
        "视频": "video",
        "图片": "image",
    }

    SORT_MAP = {
        "按修改时间": "modified",
        "按名称": "name",
        "按格式": "format",
    }

    def __init__(self, library: MediaLibrary) -> None:
        super().__init__()
        self.library = library
        self.category = QComboBox()
        self.sort = QComboBox()
        self.search = QLineEdit()
        self.list_widget = QListWidget()
        self._build()
        self.refresh()

    def _build(self) -> None:
        layout = QVBoxLayout(self)
        layout.setContentsMargins(28, 26, 28, 28)
        layout.setSpacing(16)

        title = QLabel("媒体库")
        title.setObjectName("pageTitle")
        layout.addWidget(title)

        controls = QHBoxLayout()
        self.search.setPlaceholderText("搜索名称或格式")
        self.search.textChanged.connect(self.refresh)
        self.category.addItems(self.CATEGORY_MAP.keys())
        self.category.currentTextChanged.connect(self.refresh)
        self.sort.addItems(self.SORT_MAP.keys())
        self.sort.currentTextChanged.connect(self.refresh)
        import_files = QPushButton("导入媒体")
        import_files.setObjectName("primaryButton")
        import_files.clicked.connect(self.import_files)
        controls.addWidget(self.search, 1)
        controls.addWidget(self.category)
        controls.addWidget(self.sort)
        controls.addWidget(import_files)
        layout.addLayout(controls)

        self.list_widget.setViewMode(QListWidget.ViewMode.IconMode)
        self.list_widget.setResizeMode(QListWidget.ResizeMode.Adjust)
        self.list_widget.setMovement(QListWidget.Movement.Static)
        self.list_widget.setIconSize(QSize(168, 112))
        self.list_widget.setGridSize(QSize(220, 190))
        self.list_widget.setSpacing(8)
        self.list_widget.itemDoubleClicked.connect(self._open_item)
        layout.addWidget(self.list_widget, 1)

    def set_category(self, category: str) -> None:
        reverse = {value: key for key, value in self.CATEGORY_MAP.items()}
        label = reverse.get(category, "全部媒体")
        index = self.category.findText(label)
        if index >= 0:
            self.category.setCurrentIndex(index)
        self.refresh()

    def refresh(self) -> None:
        category = self.CATEGORY_MAP.get(self.category.currentText(), "all")
        sort_by = self.SORT_MAP.get(self.sort.currentText(), "modified")
        items = self.library.filtered_items(category, self.search.text(), sort_by)
        self.list_widget.clear()
        if not items:
            empty = QListWidgetItem("暂无媒体\n点击“导入媒体”添加本地视频或图片")
            empty.setFlags(Qt.ItemFlag.NoItemFlags)
            self.list_widget.addItem(empty)
            return
        for item in items:
            self.list_widget.addItem(self._make_item(item))

    def import_files(self) -> None:
        suffixes = " ".join(f"*{suffix}" for suffix in sorted(SUPPORTED_VIDEO_EXTENSIONS | SUPPORTED_IMAGE_EXTENSIONS))
        paths, _ = QFileDialog.getOpenFileNames(self, "导入媒体", "", f"媒体文件 ({suffixes});;所有文件 (*)")
        if not paths:
            return
        self.library.import_paths(paths)
        self.library_changed.emit()
        self.refresh()

    def _make_item(self, item: MediaItem) -> QListWidgetItem:
        text = f"{item.title}\n{item.extension or '无后缀'}  {format_bytes(item.size)}"
        list_item = QListWidgetItem(media_thumbnail(item), text)
        list_item.setData(Qt.ItemDataRole.UserRole, str(item.path))
        list_item.setToolTip(str(item.path))
        return list_item

    def _open_item(self, item: QListWidgetItem) -> None:
        path = item.data(Qt.ItemDataRole.UserRole)
        if path and Path(path).exists():
            self.open_media_requested.emit(path)
