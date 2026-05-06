from __future__ import annotations

from pathlib import Path

from PySide6.QtCore import Qt, QUrl, Signal
from PySide6.QtMultimedia import QAudioOutput, QMediaPlayer
from PySide6.QtMultimediaWidgets import QVideoWidget
from PySide6.QtWidgets import QComboBox, QHBoxLayout, QLabel, QPushButton, QSlider, QVBoxLayout, QWidget


class VideoPlayerPage(QWidget):
    back_requested = Signal()
    previous_requested = Signal()
    next_requested = Signal()

    def __init__(self) -> None:
        super().__init__()
        self.player = QMediaPlayer(self)
        self.audio = QAudioOutput(self)
        self.video = QVideoWidget()
        self.player.setAudioOutput(self.audio)
        self.player.setVideoOutput(self.video)
        self.audio.setVolume(0.7)
        self.title = QLabel("视频播放")
        self.play_button = QPushButton("播放")
        self.progress = QSlider(Qt.Orientation.Horizontal)
        self.volume = QSlider(Qt.Orientation.Horizontal)
        self.time_label = QLabel("00:00 / 00:00")
        self.speed = QComboBox()
        self._build()
        self._wire_player()

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

        self.video.setStyleSheet("background: #050607;")
        layout.addWidget(self.video, 1)

        controls = QHBoxLayout()
        controls.setContentsMargins(18, 14, 18, 18)
        controls.setSpacing(10)
        previous = QPushButton("上一项")
        next_button = QPushButton("下一项")
        previous.clicked.connect(self.previous_requested.emit)
        next_button.clicked.connect(self.next_requested.emit)
        self.play_button.setObjectName("primaryButton")
        self.play_button.clicked.connect(self.toggle_playback)
        self.progress.setRange(0, 0)
        self.progress.sliderMoved.connect(self.player.setPosition)
        self.volume.setRange(0, 100)
        self.volume.setValue(70)
        self.volume.valueChanged.connect(lambda value: self.audio.setVolume(value / 100))
        self.speed.addItems(["0.5x", "1.0x", "1.25x", "1.5x", "2.0x"])
        self.speed.setCurrentText("1.0x")
        self.speed.currentTextChanged.connect(self._set_speed)
        fullscreen = QPushButton("全屏")
        fullscreen.clicked.connect(lambda: self.video.setFullScreen(not self.video.isFullScreen()))
        controls.addWidget(previous)
        controls.addWidget(self.play_button)
        controls.addWidget(next_button)
        controls.addWidget(self.progress, 1)
        controls.addWidget(self.time_label)
        controls.addWidget(QLabel("音量"))
        controls.addWidget(self.volume)
        controls.addWidget(self.speed)
        controls.addWidget(fullscreen)
        layout.addLayout(controls)

    def _wire_player(self) -> None:
        self.player.durationChanged.connect(self._duration_changed)
        self.player.positionChanged.connect(self._position_changed)
        self.player.playbackStateChanged.connect(self._state_changed)

    def open_video(self, path: str | Path) -> None:
        media_path = Path(path)
        self.title.setText(media_path.name)
        self.player.setSource(QUrl.fromLocalFile(str(media_path)))
        self.player.play()

    def stop(self) -> None:
        self.player.stop()

    def toggle_playback(self) -> None:
        if self.player.playbackState() == QMediaPlayer.PlaybackState.PlayingState:
            self.player.pause()
        else:
            self.player.play()

    def _duration_changed(self, duration: int) -> None:
        self.progress.setRange(0, duration)
        self._update_time_label(self.player.position(), duration)

    def _position_changed(self, position: int) -> None:
        if not self.progress.isSliderDown():
            self.progress.setValue(position)
        self._update_time_label(position, self.player.duration())

    def _state_changed(self, state: QMediaPlayer.PlaybackState) -> None:
        self.play_button.setText("暂停" if state == QMediaPlayer.PlaybackState.PlayingState else "播放")

    def _set_speed(self, value: str) -> None:
        try:
            self.player.setPlaybackRate(float(value.replace("x", "")))
        except ValueError:
            self.player.setPlaybackRate(1.0)

    def _update_time_label(self, position: int, duration: int) -> None:
        self.time_label.setText(f"{_format_ms(position)} / {_format_ms(duration)}")


def _format_ms(value: int) -> str:
    seconds = max(0, value // 1000)
    minutes, seconds = divmod(seconds, 60)
    hours, minutes = divmod(minutes, 60)
    if hours:
        return f"{hours:02d}:{minutes:02d}:{seconds:02d}"
    return f"{minutes:02d}:{seconds:02d}"
