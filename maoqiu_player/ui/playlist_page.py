from __future__ import annotations

from PySide6.QtWidgets import QLabel, QVBoxLayout, QWidget

from .widgets import section_title


class PlaylistPage(QWidget):
    def __init__(self) -> None:
        super().__init__()
        layout = QVBoxLayout(self)
        layout.setContentsMargins(28, 26, 28, 28)
        layout.setSpacing(16)
        title = QLabel("播放列表")
        title.setObjectName("pageTitle")
        layout.addWidget(title)
        layout.addWidget(section_title("默认列表"))
        note = QLabel("播放列表管理已预留，可在后续版本中创建多个本地播放列表。")
        note.setObjectName("mutedText")
        layout.addWidget(note)
        layout.addStretch(1)
