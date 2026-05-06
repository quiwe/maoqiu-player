from __future__ import annotations

import os
import sys

# Prefer predictable playback behavior in packaged builds. Qt documents that
# hardware texture conversion can cause rendering issues on some drivers.
os.environ.setdefault("QT_MEDIA_BACKEND", "ffmpeg")
os.environ.setdefault("QT_DISABLE_HW_TEXTURES_CONVERSION", "1")

from PySide6.QtGui import QIcon
from PySide6.QtWidgets import QApplication

from .constants import APP_CHINESE_NAME, APP_ENGLISH_NAME
from .resources import resource_path
from .ui.app_window import MainWindow
from .ui.style import apply_theme


def run() -> None:
    app = QApplication(sys.argv)
    app.setApplicationName(APP_ENGLISH_NAME)
    app.setApplicationDisplayName(APP_CHINESE_NAME)
    app.setWindowIcon(QIcon(str(resource_path("assets/icons/maoqiu-player.png"))))
    apply_theme(app, "dark")
    window = MainWindow(app)
    window.setWindowIcon(app.windowIcon())
    window.show()
    sys.exit(app.exec())
