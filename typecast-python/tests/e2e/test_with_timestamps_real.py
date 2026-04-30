"""E2E tests against the real Typecast API. Skipped when TYPECAST_API_KEY is unset."""
from __future__ import annotations

import os

import pytest

from typecast.client import Typecast
from typecast.models import TTSRequestWithTimestamps

pytestmark = pytest.mark.skipif(
    not os.environ.get("TYPECAST_API_KEY"),
    reason="TYPECAST_API_KEY not set; skipping real-API E2E.",
)

VOICE = "tc_60e5426de8b95f1d3000d7b5"


def _request(text="Hello.", language="eng"):
    return TTSRequestWithTimestamps(
        voice_id=VOICE,
        text=text,
        model="ssfm-v30",
        language=language,
        prompt={"emotion_type": "preset", "emotion_preset": "normal", "emotion_intensity": 1.0},
        seed=42,
    )


def test_e2e_no_granularity():
    client = Typecast()
    resp = client.text_to_speech_with_timestamps(_request())
    assert resp.audio_duration > 0
    assert resp.words and resp.characters


def test_e2e_word_granularity():
    client = Typecast()
    resp = client.text_to_speech_with_timestamps(_request(), granularity="word")
    assert resp.words and resp.characters is None


def test_e2e_char_granularity():
    client = Typecast()
    resp = client.text_to_speech_with_timestamps(_request(), granularity="char")
    assert resp.characters and resp.words is None


def test_e2e_jpn_char():
    client = Typecast()
    resp = client.text_to_speech_with_timestamps(
        _request(text="こんにちは。お元気ですか?", language="jpn"),
        granularity="char",
    )
    assert resp.characters and len(resp.characters) >= 5
