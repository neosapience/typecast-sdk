"""Internal helpers for instant cloning (sync/async shared)."""
from __future__ import annotations

import os
from pathlib import Path
from typing import BinaryIO, Union

CLONING_MAX_FILE_SIZE = 25 * 1024 * 1024  # must match typecast-api `cloning_max_file_size`
NAME_MIN_LENGTH = 1
NAME_MAX_LENGTH = 30

AudioInput = Union[str, Path, bytes, BinaryIO]


def validate_clone_inputs(audio: AudioInput, name: str) -> tuple[bytes, str]:
    """Pre-validate `clone_voice` inputs and return (audio_bytes, filename).

    Args:
        audio: One of file path (str/Path), raw bytes, or readable binary file object.
        name: Voice name (1-30 chars).

    Returns:
        (audio_bytes, filename) — filename is derived from the path/file object,
        or defaults to "audio.wav" when caller passes raw bytes.

    Raises:
        ValueError: name length out of range or file too large.
        FileNotFoundError: path argument refers to a non-existent file.
        TypeError: audio is none of the accepted types.
    """
    if not (NAME_MIN_LENGTH <= len(name) <= NAME_MAX_LENGTH):
        raise ValueError(
            f"name must be {NAME_MIN_LENGTH}-{NAME_MAX_LENGTH} characters; got {len(name)}"
        )

    if isinstance(audio, (str, Path)):
        path = Path(audio)
        if not path.exists() or not path.is_file():
            raise FileNotFoundError(f"audio file not found: {path}")
        audio_bytes = path.read_bytes()
        filename = path.name
    elif isinstance(audio, (bytes, bytearray)):
        audio_bytes = bytes(audio)
        filename = "audio.wav"
    elif hasattr(audio, "read"):
        audio_bytes = audio.read()
        filename = getattr(audio, "name", None) or "audio.wav"
        if filename and os.sep in filename:
            filename = os.path.basename(filename)
    else:
        raise TypeError(
            "audio must be a file path (str/Path), bytes, or readable binary file object"
        )

    if len(audio_bytes) > CLONING_MAX_FILE_SIZE:
        raise ValueError(
            f"audio file exceeds 25MB limit; got {len(audio_bytes)} bytes"
        )

    return audio_bytes, filename
