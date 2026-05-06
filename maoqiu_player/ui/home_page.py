from __future__ import annotations

from PySide6.QtCore import Signal
from PySide6.QtWidgets import QGridLayout, QHBoxLayout, QLabel, QPushButton, QScrollArea, QVBoxLayout, QWidget

from maoqiu_player.constants import APP_CHINESE_NAME, APP_ENGLISH_NAME
from maoqiu_player.models import MediaItem

from .widgets import ActionCard, InfoCard, MediaRow, section_title


class HomePage(QWidget):
    open_file_requested = Signal()
    navigate_requested = Signal(str)
    open_media_requested = Signal(str)

    def __init__(self) -> None:
        super().__init__()
        self.stats_row = QHBoxLayout()
        self.recent_layout = QVBoxLayout()
        self._build()

    def _build(self) -> None:
        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        content = QWidget()
        layout = QVBoxLayout(content)
        layout.setContentsMargins(28, 26, 28, 34)
        layout.setSpacing(22)

        title = QLabel(f"{APP_CHINESE_NAME} / {APP_ENGLISH_NAME}")
        title.setObjectName("pageTitle")
        subtitle = QLabel("本地视频、图片和播放列表集中管理。")
        subtitle.setObjectName("mutedText")
        layout.addWidget(title)
        layout.addWidget(subtitle)

        stats_widget = QWidget()
        self.stats_row.setContentsMargins(0, 0, 0, 0)
        self.stats_row.setSpacing(12)
        stats_widget.setLayout(self.stats_row)
        layout.addWidget(stats_widget)

        layout.addWidget(section_title("主要入口"))
        cards = QGridLayout()
        cards.setHorizontalSpacing(12)
        cards.setVerticalSpacing(12)
        entries = [
            ("最近播放", "继续上次观看", "recent"),
            ("本地视频", "浏览已导入视频", "video"),
            ("本地图片", "查看本地图片", "image"),
            ("播放列表", "整理播放队列", "playlist"),
            ("打开文件", "直接打开本地媒体", "open"),
            ("设置", "主题、缓存与高级设置", "settings"),
        ]
        for index, (title_text, subtitle_text, target) in enumerate(entries):
            card = ActionCard(title_text, subtitle_text)
            if target == "open":
                card.clicked.connect(self.open_file_requested.emit)
            else:
                card.clicked.connect(lambda checked=False, key=target: self.navigate_requested.emit(key))
            cards.addWidget(card, index // 3, index % 3)
        layout.addLayout(cards)

        layout.addWidget(section_title("最近播放"))
        recent_wrap = QWidget()
        self.recent_layout.setContentsMargins(0, 0, 0, 0)
        self.recent_layout.setSpacing(10)
        recent_wrap.setLayout(self.recent_layout)
        layout.addWidget(recent_wrap)
        layout.addStretch(1)

        scroll.setWidget(content)
        root.addWidget(scroll)

    def update_overview(self, stats: dict[str, int], recent_items: list[MediaItem]) -> None:
        _clear_layout(self.stats_row)
        self.stats_row.addWidget(InfoCard("媒体库", str(stats.get("total", 0)), "已导入项目"))
        self.stats_row.addWidget(InfoCard("本地视频", str(stats.get("videos", 0)), "视频文件"))
        self.stats_row.addWidget(InfoCard("本地图片", str(stats.get("images", 0)), "图片文件"))
        self.stats_row.addWidget(InfoCard("最近播放", str(stats.get("recent", 0)), "播放记录"))

        _clear_layout(self.recent_layout)
        if not recent_items:
            empty = QLabel("还没有播放历史。可以通过“打开文件”开始播放本地媒体。")
            empty.setObjectName("mutedText")
            self.recent_layout.addWidget(empty)
            return
        for item in recent_items[:5]:
            row_button = QPushButton()
            row_button.setObjectName("cardButton")
            row_layout = QVBoxLayout(row_button)
            row_layout.setContentsMargins(0, 0, 0, 0)
            row_layout.addWidget(MediaRow(item))
            row_button.clicked.connect(lambda checked=False, path=str(item.path): self.open_media_requested.emit(path))
            self.recent_layout.addWidget(row_button)


def _clear_layout(layout) -> None:
    while layout.count():
        item = layout.takeAt(0)
        widget = item.widget()
        if widget is not None:
            widget.deleteLater()
