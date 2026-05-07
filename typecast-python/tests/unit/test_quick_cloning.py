"""Unit tests for quick voice cloning DX."""
import io
from pathlib import Path

import pytest

from typecast._voice_clone import validate_clone_inputs
from typecast.models import CustomVoice

CLONING_MAX_FILE_SIZE = 25 * 1024 * 1024


def test_custom_voice_parses_response():
    payload = {"voice_id": "uc_64a1b2c3d4e5f6a7b8c9d0e1", "name": "demo", "model": "ssfm-v30"}
    voice = CustomVoice.model_validate(payload)
    assert voice.voice_id == "uc_64a1b2c3d4e5f6a7b8c9d0e1"
    assert voice.name == "demo"
    assert voice.model == "ssfm-v30"


def test_validate_rejects_file_too_large():
    big = b"\x00" * (CLONING_MAX_FILE_SIZE + 1)
    with pytest.raises(ValueError, match="audio file exceeds 25MB limit"):
        validate_clone_inputs(big, "demo")


def test_validate_rejects_empty_name():
    with pytest.raises(ValueError, match="name must be 1-30 characters"):
        validate_clone_inputs(b"\x00" * 1024, "")


def test_validate_rejects_long_name():
    with pytest.raises(ValueError, match="name must be 1-30 characters"):
        validate_clone_inputs(b"\x00" * 1024, "x" * 31)


def test_validate_rejects_missing_path(tmp_path):
    missing = tmp_path / "nope.wav"
    with pytest.raises(FileNotFoundError):
        validate_clone_inputs(missing, "demo")


def test_validate_accepts_path(tmp_path):
    p = tmp_path / "ok.wav"
    p.write_bytes(b"\x00" * 1024)
    audio_bytes, filename = validate_clone_inputs(p, "demo")
    assert audio_bytes == b"\x00" * 1024
    assert filename == "ok.wav"


def test_validate_accepts_bytes_with_default_filename():
    audio_bytes, filename = validate_clone_inputs(b"\x00" * 1024, "demo")
    assert audio_bytes == b"\x00" * 1024
    assert filename == "audio.wav"


def test_validate_accepts_file_object():
    buf = io.BytesIO(b"\x00" * 2048)
    buf.name = "foo.mp3"
    audio_bytes, filename = validate_clone_inputs(buf, "demo")
    assert audio_bytes == b"\x00" * 2048
    assert filename == "foo.mp3"
