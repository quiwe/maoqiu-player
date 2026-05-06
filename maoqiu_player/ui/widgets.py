from __future__ import annotations

from datetime import datetime
from pathlib import Path

from PySide6.QtCore import QSize, Qt
from PySide6.QtGui import QColor, QIcon, QPainter, QPixmap
from PySide6.QtWidgets import QFrame, QHBoxLayout, QLabel, QPushButton, QVBoxLayout

from maoqiu_player.models import MediaItem, format_media_type


def format_bytes(size: int) -> str:
    value = float(size)
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if value < 1024 or unit == "TB":
            return f"{value:.1f} {unit}" if unit != "B" else f"{int(value)} B"
        value /= 1024
    return f"{value:.1f} TB"


def format_time(value: datetime | None) -> str:
    if value is None:
        return "尚未播放"
    return value.strftime("%Y-%m-%d %H:%M")


def section_title(text: str) -> QLabel:
    label = QLabel(text)
    label.setObjectName("sectionTitle")
    return label


class InfoCard(QFrame):
    def __init__(self, title: str, value: str, subtitle: str = "") -> None:
        super().__init__()
        self.setObjectName("statCard")
        layout = QVBoxLayout(self)
        layout.setContentsMargins(16, 14, 16, 14)
        layout.setSpacing(6)
        value_label = QLabel(value)
        value_label.setObjectName("smallMetric")
        title_label = QLabel(title)
        title_label.setObjectName("mutedText")
        layout.addWidget(value_label)
        layout.addWidget(title_label)
        if subtitle:
            sub = QLabel(subtitle)
            sub.setObjectName("mutedText")
            layout.addWidget(sub)


class ActionCard(QPushButton):
    def __init__(self, title: str, subtitle: str) -> None:
        super().__init__(f"{title}\n{subtitle}")
        self.setObjectName("cardButton")
        self.setMinimumHeight(92)
        self.setCursor(Qt.CursorShape.PointingHandCursor)


class MediaRow(QFrame):
    def __init__(self, item: MediaItem) -> None:
        super().__init__()
        self.setObjectName("surfaceCard")
        layout = QHBoxLayout(self)
        layout.setContentsMargins(12, 10, 12, 10)
        layout.setSpacing(12)
        cover = QLabel()
        cover.setFixedSize(76, 52)
        cover.setPixmap(media_thumbnail(item).pixmap(QSize(76, 52)))
        layout.addWidget(cover)
        text_layout = QVBoxLayout()
        title = QLabel(item.title)
        title.setObjectName("sectionTitle")
        meta = QLabel(f"{format_media_type(item.media_type)}  {item.extension or '无后缀'}  {format_bytes(item.size)}")
        meta.setObjectName("mutedText")
        text_layout.addWidget(title)
        text_layout.addWidget(meta)
        layout.addLayout(text_layout, 1)
        time_label = QLabel(format_time(item.last_played_at))
        time_label.setObjectName("mutedText")
        layout.addWidget(time_label)


def media_thumbnail(item: MediaItem, size: QSize = QSize(160, 110)) -> QIcon:
    if item.media_type == "image":
        image = QPixmap(str(item.path))
        if not image.isNull():
            scaled = image.scaled(size, Qt.AspectRatioMode.KeepAspectRatioByExpanding, Qt.TransformationMode.SmoothTransformation)
            return QIcon(scaled.copy(0, 0, min(size.width(), scaled.width()), min(size.height(), scaled.height())))
    return QIcon(_placeholder_pixmap(item.media_type, size))


def _placeholder_pixmap(media_type: str, size: QSize) -> QPixmap:
    pixmap = QPixmap(size)
    if media_type == "video":
        background = QColor("#2fa88f")
        text = "VIDEO"
    elif media_type == "image":
        background = QColor("#f2b84b")
        text = "IMAGE"
    else:
        background = QColor("#5a6472")
        text = "MEDIA"
    pixmap.fill(background)
    painter = QPainter(pixmap)
    painter.setRenderHint(QPainter.RenderHint.Antialiasing)
    painter.setPen(QColor("#101113"))
    font = painter.font()
    font.setBold(True)
    font.setPointSize(11)
    painter.setFont(font)
    painter.drawText(pixmap.rect(), Qt.AlignmentFlag.AlignCenter, text)
    painter.end()
    return pixmap


def path_display(path: str | Path) -> str:
    text = str(path)
    return text if len(text) <= 72 else f"...{text[-69:]}"
