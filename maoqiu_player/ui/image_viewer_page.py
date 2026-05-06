from __future__ import annotations

from pathlib import Path

from PySide6.QtCore import QEvent, QRectF, QSize, Qt, Signal
from PySide6.QtGui import QColor, QImage, QImageReader, QPainter, QTransform
from PySide6.QtWidgets import QHBoxLayout, QLabel, QPushButton, QScrollArea, QSlider, QVBoxLayout, QWidget


class ImageViewerPage(QWidget):
    back_requested = Signal()

    def __init__(self) -> None:
        super().__init__()
        self.paths: list[Path] = []
        self.index = 0
        self.rotation = 0
        self.title = QLabel("图片查看")
        self.zoom_label = QLabel("适应窗口")
        self.scroll = QScrollArea()
        self.canvas = ImageCanvas()
        self.zoom_slider = QSlider(Qt.Orientation.Horizontal)
        self._syncing_zoom = False
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

        self.scroll.setWidgetResizable(False)
        self.scroll.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.scroll.setWidget(self.canvas)
        self.scroll.viewport().installEventFilter(self)
        layout.addWidget(self.scroll, 1)

        controls = QHBoxLayout()
        controls.setContentsMargins(18, 14, 18, 18)
        previous = QPushButton("上一张")
        next_button = QPushButton("下一张")
        fit = QPushButton("适应窗口")
        actual_size = QPushButton("实际大小")
        rotate = QPushButton("旋转")
        previous.clicked.connect(self.previous_image)
        next_button.clicked.connect(self.next_image)
        fit.clicked.connect(self.fit_window)
        actual_size.clicked.connect(self.actual_size)
        rotate.clicked.connect(self.rotate_image)
        self.zoom_slider.setRange(10, 500)
        self.zoom_slider.setValue(100)
        self.zoom_slider.valueChanged.connect(self.set_zoom)
        controls.addWidget(previous)
        controls.addWidget(next_button)
        controls.addWidget(QLabel("缩放"))
        controls.addWidget(self.zoom_label)
        controls.addWidget(self.zoom_slider, 1)
        controls.addWidget(fit)
        controls.addWidget(actual_size)
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
        self.rotation = 0
        self._set_slider_value(100)
        self.canvas.set_fit_to_window(True)
        self._load_current()

    def eventFilter(self, watched, event) -> bool:
        if watched is self.scroll.viewport() and event.type() == QEvent.Type.Resize:
            self.canvas.set_viewport_size(self.scroll.viewport().size())
            self._update_zoom_label()
        return super().eventFilter(watched, event)

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
        if self._syncing_zoom:
            return
        self.canvas.set_zoom_percent(value)
        self._update_zoom_label()

    def fit_window(self) -> None:
        self.canvas.set_fit_to_window(True)
        self._set_slider_value(max(10, min(500, round(self.canvas.effective_zoom_percent()))))
        self._update_zoom_label()

    def actual_size(self) -> None:
        self._set_slider_value(100)
        self.canvas.set_zoom_percent(100)
        self._update_zoom_label()

    def rotate_image(self) -> None:
        self.rotation = (self.rotation + 90) % 360
        self.canvas.set_rotation(self.rotation)
        self._set_slider_value(max(10, min(500, round(self.canvas.effective_zoom_percent()))))
        self._update_zoom_label()

    def _load_current(self) -> None:
        if not self.paths:
            return
        path = self.paths[self.index]
        reader = QImageReader(str(path))
        reader.setAutoTransform(True)
        image = reader.read()
        self.title.setText(path.name)
        if image.isNull():
            self.canvas.set_error("无法显示该图片")
            return
        self.canvas.set_image(image)
        self.canvas.set_rotation(self.rotation)
        self.canvas.set_viewport_size(self.scroll.viewport().size())
        self._set_slider_value(max(10, min(500, round(self.canvas.effective_zoom_percent()))))
        self._update_zoom_label()

    def _set_slider_value(self, value: int) -> None:
        self._syncing_zoom = True
        self.zoom_slider.setValue(value)
        self._syncing_zoom = False

    def _update_zoom_label(self) -> None:
        if self.canvas.fit_to_window:
            self.zoom_label.setText(f"适应 {round(self.canvas.effective_zoom_percent())}%")
        else:
            self.zoom_label.setText(f"{round(self.canvas.effective_zoom_percent())}%")


class ImageCanvas(QWidget):
    def __init__(self) -> None:
        super().__init__()
        self.image = QImage()
        self.error_text = ""
        self.rotation = 0
        self.zoom = 1.0
        self.fit_to_window = True
        self.viewport_size = QSize(1, 1)
        self.setMinimumSize(1, 1)

    def set_image(self, image: QImage) -> None:
        self.image = image
        self.error_text = ""
        self._update_canvas_size()

    def set_error(self, message: str) -> None:
        self.image = QImage()
        self.error_text = message
        self.resize(max(self.viewport_size.width(), 320), max(self.viewport_size.height(), 220))
        self.update()

    def set_rotation(self, rotation: int) -> None:
        self.rotation = rotation % 360
        self._update_canvas_size()

    def set_zoom_percent(self, value: int) -> None:
        self.fit_to_window = False
        self.zoom = max(0.1, value / 100)
        self._update_canvas_size()

    def set_fit_to_window(self, enabled: bool) -> None:
        self.fit_to_window = enabled
        self._update_canvas_size()

    def set_viewport_size(self, size: QSize) -> None:
        self.viewport_size = QSize(max(1, size.width()), max(1, size.height()))
        self._update_canvas_size()

    def effective_zoom_percent(self) -> float:
        if self.image.isNull():
            return 100
        if self.fit_to_window:
            return self._fit_scale() * 100
        return self.zoom * 100

    def paintEvent(self, event) -> None:
        painter = QPainter(self)
        painter.fillRect(self.rect(), QColor("#050607"))
        painter.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform, True)

        if self.image.isNull():
            painter.setPen(QColor("#d7d0c5"))
            painter.drawText(self.rect(), Qt.AlignmentFlag.AlignCenter, self.error_text or "没有图片")
            return

        image = self._display_image()
        target_size = self._target_size(image.size())
        x = max(0, (self.width() - target_size.width()) / 2)
        y = max(0, (self.height() - target_size.height()) / 2)
        target = QRectF(x, y, target_size.width(), target_size.height())
        if target_size == image.size():
            painter.drawImage(target.topLeft(), image)
        else:
            painter.drawImage(target, image, QRectF(image.rect()))

    def _update_canvas_size(self) -> None:
        if self.image.isNull():
            self.resize(max(self.viewport_size.width(), 320), max(self.viewport_size.height(), 220))
            self.update()
            return
        target = self._target_size(self._display_image().size())
        if self.fit_to_window:
            width = max(self.viewport_size.width(), target.width())
            height = max(self.viewport_size.height(), target.height())
        else:
            width = target.width()
            height = target.height()
        self.resize(max(1, width), max(1, height))
        self.update()

    def _display_image(self) -> QImage:
        if self.rotation == 0:
            return self.image
        return self.image.transformed(QTransform().rotate(self.rotation), Qt.TransformationMode.SmoothTransformation)

    def _target_size(self, image_size: QSize) -> QSize:
        scale = self._fit_scale() if self.fit_to_window else self.zoom
        return QSize(max(1, round(image_size.width() * scale)), max(1, round(image_size.height() * scale)))

    def _fit_scale(self) -> float:
        image = self._display_image()
        if image.isNull():
            return 1.0
        available_width = max(1, self.viewport_size.width() - 20)
        available_height = max(1, self.viewport_size.height() - 20)
        return min(1.0, available_width / image.width(), available_height / image.height())
