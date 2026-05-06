from __future__ import annotations

from PySide6.QtWidgets import QApplication


DARK_THEME = """
QWidget {
    background: #111214;
    color: #f4f1ea;
    font-family: "SF Pro Text", "Segoe UI", "Microsoft YaHei";
    font-size: 14px;
}

QMainWindow#mainWindow {
    background: #111214;
}

QFrame#sidebar {
    background: #18191d;
    border-right: 1px solid #2b2d33;
}

QFrame#topBar {
    background: #111214;
    border-bottom: 1px solid #25272d;
}

QFrame#surfaceCard, QFrame#statCard {
    background: #202226;
    border: 1px solid #30333a;
    border-radius: 8px;
}

QPushButton {
    background: #26292f;
    border: 1px solid #383c44;
    border-radius: 7px;
    padding: 9px 13px;
    color: #f4f1ea;
    font-weight: 600;
}

QPushButton:hover {
    background: #30343b;
    border-color: #4a505a;
}

QPushButton:pressed {
    background: #1f8575;
}

QPushButton#primaryButton {
    background: #2fa88f;
    border-color: #35b99d;
    color: #08100f;
}

QPushButton#primaryButton:hover {
    background: #43c3a7;
}

QPushButton#ghostButton {
    background: transparent;
    border-color: #393d44;
}

QPushButton#navButton {
    background: transparent;
    border: 0;
    border-radius: 7px;
    color: #c9c2b8;
    padding: 11px 13px;
    text-align: left;
}

QPushButton#navButton:hover {
    background: #24262b;
    color: #ffffff;
}

QPushButton#navButton:checked {
    background: #203b36;
    color: #7be0c5;
}

QPushButton#cardButton {
    background: #202226;
    border: 1px solid #30333a;
    border-radius: 8px;
    text-align: left;
    padding: 14px;
}

QPushButton#cardButton:hover {
    background: #272a30;
    border-color: #3fbf9f;
}

QLineEdit, QComboBox, QListWidget, QTextEdit, QSlider {
    background: #1a1c20;
    border: 1px solid #343840;
    border-radius: 7px;
    padding: 8px 10px;
    color: #f4f1ea;
    selection-background-color: #2fa88f;
}

QLineEdit:focus, QComboBox:focus, QListWidget:focus, QTextEdit:focus {
    border-color: #3fbf9f;
}

QLineEdit#searchField {
    background: #202226;
    min-height: 26px;
    border-radius: 8px;
}

QListWidget {
    outline: 0;
}

QListWidget::item {
    background: #202226;
    border: 1px solid #30333a;
    border-radius: 8px;
    padding: 10px;
    margin: 6px;
}

QListWidget::item:selected {
    background: #203b36;
    border-color: #3fbf9f;
}

QTabWidget::pane {
    border: 0;
}

QTabBar::tab {
    background: #202226;
    color: #c9c2b8;
    padding: 10px 18px;
    border-top-left-radius: 7px;
    border-top-right-radius: 7px;
    margin-right: 4px;
}

QTabBar::tab:selected {
    background: #2fa88f;
    color: #08100f;
}

QLabel {
    background: transparent;
}

QLabel#brandTitle {
    color: #ffffff;
    font-size: 20px;
    font-weight: 800;
}

QLabel#pageTitle {
    color: #ffffff;
    font-size: 28px;
    font-weight: 800;
}

QLabel#sectionTitle {
    color: #ffffff;
    font-size: 18px;
    font-weight: 700;
}

QLabel#mutedText {
    color: #a9a196;
}

QLabel#smallMetric {
    color: #f2b84b;
    font-size: 24px;
    font-weight: 800;
}

QScrollArea {
    border: 0;
    background: transparent;
}

QScrollBar:vertical {
    background: #15161a;
    width: 10px;
}

QScrollBar::handle:vertical {
    background: #3a3e45;
    border-radius: 5px;
}
"""


LIGHT_THEME = """
QWidget {
    background: #f7f8f7;
    color: #202326;
    font-family: "SF Pro Text", "Segoe UI", "Microsoft YaHei";
    font-size: 14px;
}

QMainWindow#mainWindow {
    background: #f7f8f7;
}

QFrame#sidebar {
    background: #ffffff;
    border-right: 1px solid #dde2e2;
}

QFrame#topBar {
    background: #f7f8f7;
    border-bottom: 1px solid #dde2e2;
}

QFrame#surfaceCard, QFrame#statCard {
    background: #ffffff;
    border: 1px solid #dde2e2;
    border-radius: 8px;
}

QPushButton {
    background: #ffffff;
    border: 1px solid #cdd5d4;
    border-radius: 7px;
    padding: 9px 13px;
    color: #202326;
    font-weight: 600;
}

QPushButton:hover {
    background: #eef4f2;
    border-color: #7bb9aa;
}

QPushButton#primaryButton {
    background: #188a75;
    border-color: #188a75;
    color: #ffffff;
}

QPushButton#ghostButton {
    background: transparent;
}

QPushButton#navButton {
    background: transparent;
    border: 0;
    border-radius: 7px;
    color: #59605f;
    padding: 11px 13px;
    text-align: left;
}

QPushButton#navButton:hover {
    background: #eff4f2;
    color: #202326;
}

QPushButton#navButton:checked {
    background: #dcefe9;
    color: #126d5e;
}

QPushButton#cardButton {
    background: #ffffff;
    border: 1px solid #dde2e2;
    border-radius: 8px;
    text-align: left;
    padding: 14px;
}

QPushButton#cardButton:hover {
    background: #f1f7f5;
    border-color: #188a75;
}

QLineEdit, QComboBox, QListWidget, QTextEdit, QSlider {
    background: #ffffff;
    border: 1px solid #cdd5d4;
    border-radius: 7px;
    padding: 8px 10px;
    color: #202326;
    selection-background-color: #b9ded5;
}

QLineEdit#searchField {
    min-height: 26px;
    border-radius: 8px;
}

QListWidget {
    outline: 0;
}

QListWidget::item {
    background: #ffffff;
    border: 1px solid #dde2e2;
    border-radius: 8px;
    padding: 10px;
    margin: 6px;
}

QListWidget::item:selected {
    background: #dcefe9;
    border-color: #188a75;
}

QTabWidget::pane {
    border: 0;
}

QTabBar::tab {
    background: #ffffff;
    color: #59605f;
    padding: 10px 18px;
    border-top-left-radius: 7px;
    border-top-right-radius: 7px;
    margin-right: 4px;
}

QTabBar::tab:selected {
    background: #188a75;
    color: #ffffff;
}

QLabel {
    background: transparent;
}

QLabel#brandTitle {
    color: #202326;
    font-size: 20px;
    font-weight: 800;
}

QLabel#pageTitle {
    color: #202326;
    font-size: 28px;
    font-weight: 800;
}

QLabel#sectionTitle {
    color: #202326;
    font-size: 18px;
    font-weight: 700;
}

QLabel#mutedText {
    color: #6c7371;
}

QLabel#smallMetric {
    color: #b7791f;
    font-size: 24px;
    font-weight: 800;
}

QScrollArea {
    border: 0;
    background: transparent;
}
"""


def apply_theme(app: QApplication, theme: str) -> None:
    app.setStyleSheet(LIGHT_THEME if theme == "light" else DARK_THEME)
