from __future__ import annotations

import sys

from PySide6.QtWidgets import QApplication

from .constants import APP_CHINESE_NAME, APP_ENGLISH_NAME
from .ui.app_window import MainWindow
from .ui.style import apply_theme


def run() -> None:
    app = QApplication(sys.argv)
    app.setApplicationName(APP_ENGLISH_NAME)
    app.setApplicationDisplayName(APP_CHINESE_NAME)
    apply_theme(app, "dark")
    window = MainWindow(app)
    window.show()
    sys.exit(app.exec())
