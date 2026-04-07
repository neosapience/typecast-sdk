"""Tests for typecast.utils module."""

import wave
from pathlib import Path

import pytest

from typecast.utils import show_performance


@pytest.fixture
def tiny_wav(tmp_path: Path) -> Path:
    """Create a 1-second mono 8000Hz silent WAV file."""
    path = tmp_path / "tiny.wav"
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(8000)
        w.writeframes(b"\x00\x00" * 8000)
    return path


def test_show_performance_prints_stats(tiny_wav, capsys):
    show_performance(processing_time=0.5, wave_path=str(tiny_wav))

    captured = capsys.readouterr()
    assert "Time taken" in captured.out
    assert "Audio duration" in captured.out
    assert "Number of tokens" in captured.out
    assert "Tokens per seconds" in captured.out
    # 1 second of audio at 20 tokens/sec = 20 tokens
    assert "20 tokens" in captured.out
