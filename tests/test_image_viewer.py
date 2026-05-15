from __future__ import annotations

import os
import sys

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from PySide6.QtCore import QSize
from PySide6.QtGui import QColor, QImage
from PySide6.QtWidgets import QApplication

from maoqiu_player.ui.image_viewer_page import ImageCanvas


def _app() -> QApplication:
    return QApplication.instance() or QApplication(sys.argv)


def _image(width: int, height: int) -> QImage:
    image = QImage(width, height, QImage.Format.Format_ARGB32)
    image.fill(QColor("#ffffff"))
    return image


def test_fit_to_window_upscales_small_images() -> None:
    _app()
    canvas = ImageCanvas()
    canvas.set_viewport_size(QSize(800, 600))
    canvas.set_image(_image(80, 60))
    canvas.set_fit_to_window(True)

    assert round(canvas.effective_zoom_percent()) > 100


def test_fit_to_window_scales_down_large_images() -> None:
    _app()
    canvas = ImageCanvas()
    canvas.set_viewport_size(QSize(800, 600))
    canvas.set_image(_image(4000, 3000))
    canvas.set_fit_to_window(True)

    assert canvas.effective_zoom_percent() < 100
