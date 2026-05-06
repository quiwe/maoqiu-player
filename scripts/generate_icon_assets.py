from __future__ import annotations

import sys
from pathlib import Path

from PySide6.QtCore import Qt
from PySide6.QtGui import QGuiApplication, QImage, QPainter
from PySide6.QtSvg import QSvgRenderer


ROOT = Path(__file__).resolve().parents[1]
SVG_PATH = ROOT / "assets" / "icon.svg"
ICON_DIR = ROOT / "assets" / "icons"
ANDROID_RES_DIR = ROOT / "mobile" / "android" / "app" / "src" / "main" / "res"
IOS_APP_ICON_DIR = ROOT / "mobile" / "ios" / "MaoqiuPlayer" / "Assets.xcassets" / "AppIcon.appiconset"


def render_svg(size: int) -> QImage:
    renderer = QSvgRenderer(str(SVG_PATH))
    image = QImage(size, size, QImage.Format.Format_ARGB32)
    image.fill(Qt.GlobalColor.transparent)
    painter = QPainter(image)
    painter.setRenderHint(QPainter.RenderHint.Antialiasing)
    renderer.render(painter)
    painter.end()
    return image


def main() -> None:
    QGuiApplication(sys.argv)
    ICON_DIR.mkdir(parents=True, exist_ok=True)
    sizes = [16, 24, 32, 48, 64, 128, 256, 512, 1024]
    images = {size: render_svg(size) for size in sizes}
    for size, image in images.items():
        image.save(str(ICON_DIR / f"maoqiu-player-{size}.png"))
    images[256].save(str(ICON_DIR / "maoqiu-player.png"))
    images[256].save(str(ICON_DIR / "maoqiu-player.ico"))
    images[1024].save(str(ICON_DIR / "maoqiu-player.icns"))
    write_android_icons()
    write_ios_icons()


def write_android_icons() -> None:
    density_sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for density, size in density_sizes.items():
        target_dir = ANDROID_RES_DIR / density
        target_dir.mkdir(parents=True, exist_ok=True)
        render_svg(size).save(str(target_dir / "ic_launcher.png"))


def write_ios_icons() -> None:
    IOS_APP_ICON_DIR.mkdir(parents=True, exist_ok=True)
    icon_specs = [
        ("Icon-20@2x.png", 40),
        ("Icon-20@3x.png", 60),
        ("Icon-29@2x.png", 58),
        ("Icon-29@3x.png", 87),
        ("Icon-40@2x.png", 80),
        ("Icon-40@3x.png", 120),
        ("Icon-60@2x.png", 120),
        ("Icon-60@3x.png", 180),
        ("Icon-1024.png", 1024),
    ]
    for filename, size in icon_specs:
        render_svg(size).save(str(IOS_APP_ICON_DIR / filename))


if __name__ == "__main__":
    main()
