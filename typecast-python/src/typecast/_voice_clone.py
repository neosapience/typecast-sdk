"""Internal helpers for instant cloning (sync/async shared)."""
from __future__ import annotations

import os
from pathlib import Path
from typing import BinaryIO, Union

CLONING_MAX_FILE_SIZE = 25 * 1024 * 1024  # must match typecast-api `cloning_max_file_size`
NAME_MIN_LENGTH = 1
NAME_MAX_LENGTH = 30
ALLOWED_CLONE_MODELS = frozenset({"ssfm-v21", "ssfm-v30"})
CUSTOM_VOICE_ID_PREFIX = "uc_"

AudioInput = Union[str, Path, bytes, BinaryIO]


def normalize_clone_model(model: object) -> str:
    """Coerce ``model`` to its string form and reject values outside the API contract.

    Accepts a ``TTSModel`` enum (uses ``.value``) or a string. Raises ``ValueError``
    when the resolved value is not in :data:`ALLOWED_CLONE_MODELS` so callers fail
    fast client-side instead of relying on a 422 from the API.
    """
    model_str = model.value if hasattr(model, "value") else str(model)
    if model_str not in ALLOWED_CLONE_MODELS:
        allowed = ", ".join(sorted(ALLOWED_CLONE_MODELS))
        raise ValueError(f"model must be one of: {allowed}; got {model_str!r}")
    return model_str


def validate_custom_voice_id(voice_id: str) -> None:
    """Reject non-custom voice ids before they reach the DELETE endpoint."""
    if not isinstance(voice_id, str) or not voice_id.startswith(CUSTOM_VOICE_ID_PREFIX):
        raise ValueError(
            f"voice_id must start with {CUSTOM_VOICE_ID_PREFIX!r}; got {voice_id!r}"
        )


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
        if isinstance(audio_bytes, bytearray):
            audio_bytes = bytes(audio_bytes)
        if not isinstance(audio_bytes, bytes):
            raise TypeError(
                "audio file object must be opened in binary mode and return bytes"
            )
        raw_name = getattr(audio, "name", None) or "audio.wav"
        filename = os.path.basename(str(raw_name).replace("\\", "/"))
    else:
        raise TypeError(
            "audio must be a file path (str/Path), bytes, or readable binary file object"
        )

    if len(audio_bytes) > CLONING_MAX_FILE_SIZE:
        raise ValueError(
            f"audio file exceeds 25MB limit; got {len(audio_bytes)} bytes"
        )

    return audio_bytes, filename
