from __future__ import annotations

import sys
from pathlib import Path


def resource_path(relative_path: str) -> Path:
    base = Path(getattr(sys, "_MEIPASS", Path.cwd()))
    candidates = [
        base / relative_path,
        Path.cwd() / relative_path,
        Path(__file__).resolve().parents[1] / relative_path,
    ]
    for candidate in candidates:
        if candidate.exists():
            return candidate
    return candidates[-1]
