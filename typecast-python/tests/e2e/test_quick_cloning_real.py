"""E2E tests for instant cloning against a real Typecast API.

Skipped unless ``TYPECAST_API_KEY`` is set. Set ``TYPECAST_API_HOST`` to point
at the desired Typecast API host (production or a development environment).

Each test cleans up its cloned voice via ``delete_voice`` to avoid filling the slot.
"""
from __future__ import annotations

import os
import time
from pathlib import Path

import pytest

from typecast import Typecast
from typecast.exceptions import BadRequestError, NotFoundError
from typecast.models import TTSRequest, CustomVoice


API_KEY = os.environ.get("TYPECAST_API_KEY")
SAMPLE = (
    Path(__file__).resolve().parents[3]
    / "test-fixtures"
    / "quick-cloning"
    / "sample.wav"
)

pytestmark = pytest.mark.skipif(
    not API_KEY, reason="TYPECAST_API_KEY not set; skipping real-API E2E"
)


@pytest.fixture
def client() -> Typecast:
    return Typecast()  # uses env TYPECAST_API_KEY, optional TYPECAST_API_HOST


@pytest.fixture
def cleanup(client: Typecast) -> list[str]:
    created: list[str] = []
    yield created
    for vid in created:
        try:
            client.delete_voice(vid)
        except Exception:
            pass  # best-effort cleanup


def _unique_name(prefix: str) -> str:
    return f"{prefix}-{int(time.time() * 1000)}"


def test_clone_v21(client: Typecast, cleanup: list[str]) -> None:
    voice = client.clone_voice(
        audio=SAMPLE, name=_unique_name("e2e-v21"), model="ssfm-v21"
    )
    cleanup.append(voice.voice_id)
    assert isinstance(voice, CustomVoice)
    assert voice.voice_id.startswith("uc_")
    assert voice.model == "ssfm-v21"


def test_clone_v30(client: Typecast, cleanup: list[str]) -> None:
    voice = client.clone_voice(
        audio=SAMPLE, name=_unique_name("e2e-v30"), model="ssfm-v30"
    )
    cleanup.append(voice.voice_id)
    assert voice.voice_id.startswith("uc_")
    assert voice.model == "ssfm-v30"


def test_clone_then_synthesize(client: Typecast, cleanup: list[str]) -> None:
    voice = client.clone_voice(
        audio=SAMPLE, name=_unique_name("e2e-tts"), model="ssfm-v30"
    )
    cleanup.append(voice.voice_id)
    audio = client.text_to_speech(
        TTSRequest(
            text="Cloned voice E2E test.",
            voice_id=voice.voice_id,
            model="ssfm-v30",
        )
    )
    assert len(audio.audio_data) > 1024  # sanity: more than just a header


def test_delete_voice_idempotent_on_404(client: Typecast) -> None:
    """Deleting an already-deleted voice should raise NotFoundError or BadRequestError."""
    voice = client.clone_voice(
        audio=SAMPLE, name=_unique_name("e2e-del"), model="ssfm-v30"
    )
    client.delete_voice(voice.voice_id)  # first delete: success
    with pytest.raises((NotFoundError, BadRequestError)):
        client.delete_voice(voice.voice_id)  # second delete: should fail
