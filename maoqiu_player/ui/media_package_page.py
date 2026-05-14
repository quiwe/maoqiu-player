from __future__ import annotations

from pathlib import Path

from PySide6.QtCore import Signal
from PySide6.QtWidgets import (
    QFileDialog,
    QGridLayout,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QListWidget,
    QMessageBox,
    QPushButton,
    QTabWidget,
    QTextEdit,
    QVBoxLayout,
    QWidget,
)

from maoqiu_player.constants import DEFAULT_MEDIA_PACKAGE_SUFFIX, SUPPORTED_IMAGE_EXTENSIONS, SUPPORTED_VIDEO_EXTENSIONS
from maoqiu_player.media_package import (
    MediaPackageError,
    create_media_package,
    read_media_package_info,
    verify_media_package,
)

from .widgets import format_bytes, path_display


class MediaPackagePage(QWidget):
    back_requested = Signal()

    def __init__(self) -> None:
        super().__init__()
        self.input_paths: list[str] = []
        self.path_list = QListWidget()
        self.package_name = QLineEdit()
        self.output_path = QLineEdit()
        self.access_phrase = QLineEdit()
        self.import_path = QLineEdit()
        self.info_view = QTextEdit()
        self.info_view.setReadOnly(True)
        self._build()

    def _build(self) -> None:
        layout = QVBoxLayout(self)
        layout.setContentsMargins(28, 26, 28, 28)
        layout.setSpacing(16)

        header = QHBoxLayout()
        title = QLabel("媒体包管理")
        title.setObjectName("pageTitle")
        back = QPushButton("返回高级工具")
        back.setObjectName("ghostButton")
        back.clicked.connect(self.back_requested.emit)
        header.addWidget(title, 1)
        header.addWidget(back)
        layout.addLayout(header)

        tabs = QTabWidget()
        tabs.addTab(self._build_create_tab(), "生成媒体包")
        tabs.addTab(self._build_import_tab(), "导入媒体包")
        tabs.addTab(self._build_info_tab(), "查看媒体包信息")
        tabs.addTab(self._build_verify_tab(), "校验媒体包")
        layout.addWidget(tabs, 1)

    def _build_create_tab(self) -> QWidget:
        tab = QWidget()
        grid = QGridLayout(tab)
        grid.setContentsMargins(0, 16, 0, 0)
        grid.setHorizontalSpacing(10)
        grid.setVerticalSpacing(10)

        add_video = QPushButton("添加视频")
        add_image = QPushButton("添加图片")
        clear = QPushButton("清空")
        choose_output = QPushButton("选择保存位置")
        create = QPushButton("生成媒体包")
        create.setObjectName("primaryButton")
        add_video.clicked.connect(lambda: self._add_files("video"))
        add_image.clicked.connect(lambda: self._add_files("image"))
        clear.clicked.connect(self._clear_inputs)
        choose_output.clicked.connect(self._choose_output)
        create.clicked.connect(self._create_package)
        self.access_phrase.setEchoMode(QLineEdit.EchoMode.Password)
        self.access_phrase.setPlaceholderText("访问口令（可选）")
        self.output_path.setPlaceholderText(f"默认保存为 *{DEFAULT_MEDIA_PACKAGE_SUFFIX}")

        button_row = QHBoxLayout()
        button_row.addWidget(add_video)
        button_row.addWidget(add_image)
        button_row.addWidget(clear)
        grid.addWidget(QLabel("媒体文件"), 0, 0)
        grid.addLayout(button_row, 0, 1, 1, 2)
        grid.addWidget(self.path_list, 1, 1, 1, 2)
        grid.addWidget(QLabel("媒体包名称"), 2, 0)
        grid.addWidget(self.package_name, 2, 1, 1, 2)
        grid.addWidget(QLabel("保存位置"), 3, 0)
        grid.addWidget(self.output_path, 3, 1)
        grid.addWidget(choose_output, 3, 2)
        grid.addWidget(QLabel("访问口令"), 4, 0)
        grid.addWidget(self.access_phrase, 4, 1, 1, 2)
        grid.addWidget(create, 5, 1, 1, 2)
        return tab

    def _build_import_tab(self) -> QWidget:
        tab = QWidget()
        grid = QGridLayout(tab)
        grid.setContentsMargins(0, 16, 0, 0)
        choose = QPushButton("选择媒体包")
        choose.clicked.connect(self._choose_import_path)
        read = QPushButton("读取信息")
        read.setObjectName("primaryButton")
        read.clicked.connect(self._read_info)
        grid.addWidget(QLabel("媒体包文件"), 0, 0)
        grid.addWidget(self.import_path, 0, 1)
        grid.addWidget(choose, 0, 2)
        grid.addWidget(read, 1, 1, 1, 2)
        note = QLabel("导入后可加入媒体库或用于临时播放。")
        note.setObjectName("mutedText")
        grid.addWidget(note, 2, 1, 1, 2)
        grid.setRowStretch(3, 1)
        return tab

    def _build_info_tab(self) -> QWidget:
        tab = QWidget()
        layout = QVBoxLayout(tab)
        layout.setContentsMargins(0, 16, 0, 0)
        layout.addWidget(self.info_view, 1)
        return tab

    def _build_verify_tab(self) -> QWidget:
        tab = QWidget()
        grid = QGridLayout(tab)
        grid.setContentsMargins(0, 16, 0, 0)
        verify = QPushButton("校验当前媒体包")
        verify.setObjectName("primaryButton")
        verify.clicked.connect(self._verify_current)
        grid.addWidget(QLabel("文件完整性校验"), 0, 0)
        grid.addWidget(verify, 0, 1)
        grid.setRowStretch(1, 1)
        return tab

    def _add_files(self, media_type: str) -> None:
        suffixes = SUPPORTED_VIDEO_EXTENSIONS if media_type == "video" else SUPPORTED_IMAGE_EXTENSIONS
        label = "视频" if media_type == "video" else "图片"
        filter_text = " ".join(f"*{suffix}" for suffix in sorted(suffixes))
        paths, _ = QFileDialog.getOpenFileNames(self, f"添加{label}", "", f"{label}文件 ({filter_text});;所有文件 (*)")
        for path in paths:
            if path not in self.input_paths:
                self.input_paths.append(path)
                self.path_list.addItem(path_display(path))
        if paths and not self.output_path.text():
            self.output_path.setText(str(Path(paths[0]).with_name(f"{Path(paths[0]).stem}{DEFAULT_MEDIA_PACKAGE_SUFFIX}")))

    def _clear_inputs(self) -> None:
        self.input_paths.clear()
        self.path_list.clear()

    def _choose_output(self) -> None:
        default = self.output_path.text() or f"media-package{DEFAULT_MEDIA_PACKAGE_SUFFIX}"
        path, _ = QFileDialog.getSaveFileName(self, "保存媒体包", default, f"媒体包 (*{DEFAULT_MEDIA_PACKAGE_SUFFIX});;所有文件 (*)")
        if path:
            self.output_path.setText(path)

    def _choose_import_path(self) -> None:
        path, _ = QFileDialog.getOpenFileName(self, "选择媒体包", "", f"媒体包 (*{DEFAULT_MEDIA_PACKAGE_SUFFIX});;所有文件 (*)")
        if path:
            self.import_path.setText(path)
            self._read_info()

    def _create_package(self) -> None:
        try:
            info = create_media_package(
                self.input_paths,
                self.output_path.text() or f"media-package{DEFAULT_MEDIA_PACKAGE_SUFFIX}",
                package_name=self.package_name.text().strip() or None,
                access_phrase=self.access_phrase.text().strip() or None,
            )
        except MediaPackageError as exc:
            QMessageBox.warning(self, "生成失败", str(exc))
            return
        self.import_path.setText(str(info.path))
        self._render_info(info.path)
        QMessageBox.information(self, "完成", "媒体包已生成。")

    def _read_info(self) -> None:
        if not self.import_path.text().strip():
            QMessageBox.information(self, "媒体包管理", "请选择媒体包文件。")
            return
        try:
            self._render_info(self.import_path.text())
        except MediaPackageError as exc:
            QMessageBox.warning(self, "读取失败", str(exc))

    def _verify_current(self) -> None:
        if not self.import_path.text().strip():
            QMessageBox.information(self, "媒体包管理", "请先选择媒体包。")
            return
        try:
            ok = verify_media_package(self.import_path.text())
        except MediaPackageError as exc:
            QMessageBox.warning(self, "校验失败", str(exc))
            return
        QMessageBox.information(self, "校验结果", "媒体包完整。" if ok else "媒体包校验未通过。")

    def _render_info(self, path: str | Path) -> None:
        info = read_media_package_info(path)
        lines = [
            f"名称：{info.package_name}",
            f"格式：{info.format_name}",
            f"文件：{info.path}",
            f"创建时间：{info.created_at}",
            f"项目数量：{info.item_count}",
            f"包体大小：{format_bytes(info.payload_size)}",
        ]
        if info.requires_credentials:
            lines.append("提示：该文件需要用户名和密码，打开后才能读取内部媒体信息。")
        lines.extend(["", "内容："])
        for item in info.items:
            lines.append(f"- {item.get('name')}  {item.get('media_type')}  {format_bytes(int(item.get('size', 0)))}")
        self.info_view.setPlainText("\n".join(lines))
