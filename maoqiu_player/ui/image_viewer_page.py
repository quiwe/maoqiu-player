from __future__ import annotations

from pathlib import Path

from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QPixmap, QTransform
from PySide6.QtWidgets import QHBoxLayout, QLabel, QPushButton, QScrollArea, QSlider, QVBoxLayout, QWidget


class ImageViewerPage(QWidget):
    back_requested = Signal()

    def __init__(self) -> None:
        super().__init__()
        self.paths: list[Path] = []
        self.index = 0
        self.zoom = 100
        self.rotation = 0
        self.pixmap = QPixmap()
        self.title = QLabel("图片查看")
        self.image_label = QLabel()
        self.image_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.zoom_slider = QSlider(Qt.Orientation.Horizontal)
        self._build()

    def _build(self) -> None:
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)

        header = QHBoxLayout()
        header.setContentsMargins(22, 18, 22, 12)
        back = QPushButton("返回")
        back.setObjectName("ghostButton")
        back.clicked.connect(self.back_requested.emit)
        self.title.setObjectName("sectionTitle")
        header.addWidget(back)
        header.addWidget(self.title, 1)
        layout.addLayout(header)

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setAlignment(Qt.AlignmentFlag.AlignCenter)
        scroll.setWidget(self.image_label)
        layout.addWidget(scroll, 1)

        controls = QHBoxLayout()
        controls.setContentsMargins(18, 14, 18, 18)
        previous = QPushButton("上一张")
        next_button = QPushButton("下一张")
        fit = QPushButton("适应窗口")
        rotate = QPushButton("旋转")
        previous.clicked.connect(self.previous_image)
        next_button.clicked.connect(self.next_image)
        fit.clicked.connect(self.fit_window)
        rotate.clicked.connect(self.rotate_image)
        self.zoom_slider.setRange(25, 300)
        self.zoom_slider.setValue(100)
        self.zoom_slider.valueChanged.connect(self.set_zoom)
        controls.addWidget(previous)
        controls.addWidget(next_button)
        controls.addWidget(QLabel("缩放"))
        controls.addWidget(self.zoom_slider, 1)
        controls.addWidget(fit)
        controls.addWidget(rotate)
        layout.addLayout(controls)

    def open_image(self, path: str | Path, playlist: list[str | Path] | None = None) -> None:
        target = Path(path)
        self.paths = [Path(item) for item in playlist] if playlist else [target]
        if target in self.paths:
            self.index = self.paths.index(target)
        else:
            self.paths.insert(0, target)
            self.index = 0
        self.zoom = 100
        self.rotation = 0
        self.zoom_slider.setValue(100)
        self._load_current()

    def previous_image(self) -> None:
        if not self.paths:
            return
        self.index = (self.index - 1) % len(self.paths)
        self._load_current()

    def next_image(self) -> None:
        if not self.paths:
            return
        self.index = (self.index + 1) % len(self.paths)
        self._load_current()

    def set_zoom(self, value: int) -> None:
        self.zoom = value
        self._render()

    def fit_window(self) -> None:
        self.zoom_slider.setValue(100)
        self.image_label.setScaledContents(False)
        self._render()

    def rotate_image(self) -> None:
        self.rotation = (self.rotation + 90) % 360
        self._render()

    def _load_current(self) -> None:
        if not self.paths:
            return
        path = self.paths[self.index]
        self.pixmap = QPixmap(str(path))
        self.title.setText(path.name)
        self._render()

    def _render(self) -> None:
        if self.pixmap.isNull():
            self.image_label.setText("无法显示该图片")
            return
        transformed = self.pixmap.transformed(QTransform().rotate(self.rotation), Qt.TransformationMode.SmoothTransformation)
        width = max(1, int(transformed.width() * self.zoom / 100))
        height = max(1, int(transformed.height() * self.zoom / 100))
        scaled = transformed.scaled(width, height, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation)
        self.image_label.setPixmap(scaled)
        self.image_label.resize(scaled.size())
