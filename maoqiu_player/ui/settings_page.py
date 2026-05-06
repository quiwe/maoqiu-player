from __future__ import annotations

from PySide6.QtCore import Signal
from PySide6.QtWidgets import QComboBox, QFrame, QGridLayout, QLabel, QPushButton, QVBoxLayout, QWidget

from maoqiu_player.constants import APP_CHINESE_NAME, APP_ENGLISH_NAME, APP_VERSION, CACHE_DIR


class SettingsPage(QWidget):
    theme_changed = Signal(str)
    advanced_tools_requested = Signal()

    def __init__(self) -> None:
        super().__init__()
        self.theme = QComboBox()
        self._build()

    def _build(self) -> None:
        layout = QVBoxLayout(self)
        layout.setContentsMargins(28, 26, 28, 28)
        layout.setSpacing(16)

        title = QLabel("设置")
        title.setObjectName("pageTitle")
        layout.addWidget(title)

        layout.addWidget(self._section("常规", [("界面名称", APP_CHINESE_NAME), ("英文名称", APP_ENGLISH_NAME)]))
        theme_card = QFrame()
        theme_card.setObjectName("surfaceCard")
        theme_layout = QGridLayout(theme_card)
        theme_layout.setContentsMargins(16, 14, 16, 14)
        theme_layout.addWidget(QLabel("播放设置"), 0, 0)
        playback = QLabel("默认音量、倍速、连续播放等选项已预留。")
        playback.setObjectName("mutedText")
        theme_layout.addWidget(playback, 1, 0)
        theme_layout.addWidget(QLabel("主题"), 0, 1)
        self.theme.addItems(["深色主题", "浅色主题"])
        self.theme.currentTextChanged.connect(lambda text: self.theme_changed.emit("light" if text == "浅色主题" else "dark"))
        theme_layout.addWidget(self.theme, 1, 1)
        layout.addWidget(theme_card)

        layout.addWidget(self._section("媒体库", [("扫描范围", "手动导入本地视频和图片"), ("排序", "名称、格式、时间")]))
        layout.addWidget(self._section("缓存", [("缓存位置", str(CACHE_DIR)), ("策略", "播放媒体包时使用临时缓存")]))

        advanced = QFrame()
        advanced.setObjectName("surfaceCard")
        advanced_layout = QGridLayout(advanced)
        advanced_layout.setContentsMargins(16, 14, 16, 14)
        advanced_layout.addWidget(QLabel("高级设置"), 0, 0)
        note = QLabel("高级工具、媒体包管理、缓存清理、数据库维护和文件校验。")
        note.setObjectName("mutedText")
        advanced_layout.addWidget(note, 1, 0)
        button = QPushButton("高级工具")
        button.setObjectName("primaryButton")
        button.clicked.connect(self.advanced_tools_requested.emit)
        advanced_layout.addWidget(button, 0, 1, 2, 1)
        layout.addWidget(advanced)

        about = self._section(
            f"关于{APP_CHINESE_NAME}",
            [
                ("版本", APP_VERSION),
                ("定位", "跨平台本地媒体播放器"),
                ("媒体包", "支持专用格式 .mqp"),
            ],
        )
        layout.addWidget(about)
        layout.addStretch(1)

    def _section(self, title: str, rows: list[tuple[str, str]]) -> QFrame:
        frame = QFrame()
        frame.setObjectName("surfaceCard")
        grid = QGridLayout(frame)
        grid.setContentsMargins(16, 14, 16, 14)
        title_label = QLabel(title)
        title_label.setObjectName("sectionTitle")
        grid.addWidget(title_label, 0, 0, 1, 2)
        for row, (label, value) in enumerate(rows, start=1):
            name = QLabel(label)
            name.setObjectName("mutedText")
            text = QLabel(value)
            text.setWordWrap(True)
            grid.addWidget(name, row, 0)
            grid.addWidget(text, row, 1)
        return frame
