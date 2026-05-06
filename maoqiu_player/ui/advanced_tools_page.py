from __future__ import annotations

from PySide6.QtCore import Signal
from PySide6.QtWidgets import QGridLayout, QLabel, QMessageBox, QPushButton, QVBoxLayout, QWidget

from .widgets import ActionCard


class AdvancedToolsPage(QWidget):
    back_requested = Signal()
    media_package_requested = Signal()

    def __init__(self) -> None:
        super().__init__()
        self._build()

    def _build(self) -> None:
        layout = QVBoxLayout(self)
        layout.setContentsMargins(28, 26, 28, 28)
        layout.setSpacing(16)

        header = QGridLayout()
        title = QLabel("高级工具")
        title.setObjectName("pageTitle")
        back = QPushButton("返回设置")
        back.setObjectName("ghostButton")
        back.clicked.connect(self.back_requested.emit)
        header.addWidget(title, 0, 0)
        header.addWidget(back, 0, 1)
        layout.addLayout(header)

        grid = QGridLayout()
        grid.setHorizontalSpacing(12)
        grid.setVerticalSpacing(12)
        actions = [
            ("媒体包管理", "生成、导入、查看和校验媒体包", self.media_package_requested.emit),
            ("清理缓存", "移除临时播放缓存", lambda: self._notice("缓存清理已预留。")),
            ("数据库维护", "刷新媒体库索引和记录", lambda: self._notice("数据库维护已预留。")),
            ("文件校验", "检查媒体文件完整性", lambda: self._notice("文件校验已预留。")),
        ]
        for index, (title_text, subtitle, callback) in enumerate(actions):
            card = ActionCard(title_text, subtitle)
            card.clicked.connect(callback)
            grid.addWidget(card, index // 2, index % 2)
        layout.addLayout(grid)
        layout.addStretch(1)

    def _notice(self, message: str) -> None:
        QMessageBox.information(self, "高级工具", message)
