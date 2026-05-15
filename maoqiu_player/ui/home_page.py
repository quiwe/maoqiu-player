from __future__ import annotations

from PySide6.QtCore import Signal
from PySide6.QtWidgets import QGridLayout, QHBoxLayout, QLabel, QScrollArea, QVBoxLayout, QWidget

from maoqiu_player.constants import APP_CHINESE_NAME, APP_ENGLISH_NAME

from .widgets import ActionCard, InfoCard, section_title


class HomePage(QWidget):
    open_file_requested = Signal()
    navigate_requested = Signal(str)
    open_media_requested = Signal(str)

    def __init__(self) -> None:
        super().__init__()
        self.stats_row = QHBoxLayout()
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
            ("本地视频", "浏览已导入视频", "video"),
            ("本地图片", "查看本地图片", "image"),
            ("播放列表", "整理播放队列", "playlist"),
            ("媒体包", "生成、导入和校验", "packages"),
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
        layout.addStretch(1)

        scroll.setWidget(content)
        root.addWidget(scroll)

    def update_overview(self, stats: dict[str, int]) -> None:
        _clear_layout(self.stats_row)
        self.stats_row.addWidget(InfoCard("媒体库", str(stats.get("total", 0)), "已导入项目"))
        self.stats_row.addWidget(InfoCard("本地视频", str(stats.get("videos", 0)), "视频文件"))
        self.stats_row.addWidget(InfoCard("本地图片", str(stats.get("images", 0)), "图片文件"))


def _clear_layout(layout) -> None:
    while layout.count():
        item = layout.takeAt(0)
        widget = item.widget()
        if widget is not None:
            widget.deleteLater()
