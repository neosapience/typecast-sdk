"""Unit tests for the timestamp TTS wrapping (request/response models, helpers, client)."""
from __future__ import annotations

import json
from pathlib import Path

import pytest

REPO_ROOT = Path(__file__).resolve().parents[3]
FIXTURE_DIR = REPO_ROOT / "test-fixtures" / "with-timestamps"


def _load(name: str) -> dict:
    return json.loads((FIXTURE_DIR / name).read_text(encoding="utf-8"))


class TestAlignmentSegmentModels:
    def test_word_segment_validates(self):
        from typecast.models import AlignmentSegmentWord

        seg = AlignmentSegmentWord(text="Hello.", start=0.105, end=0.523)
        assert seg.text == "Hello."
        assert seg.start == 0.105
        assert seg.end == 0.523

    def test_character_segment_validates(self):
        from typecast.models import AlignmentSegmentCharacter

        seg = AlignmentSegmentCharacter(text="H", start=0.105, end=0.161)
        assert seg.end > seg.start

    def test_segment_required_fields(self):
        from pydantic import ValidationError
        from typecast.models import AlignmentSegmentWord

        with pytest.raises(ValidationError):
            AlignmentSegmentWord(text="x", start=0.0)  # type: ignore[call-arg]


class TestRequestResponseSchemas:
    def test_request_minimal(self):
        from typecast.models import TTSRequestWithTimestamps

        req = TTSRequestWithTimestamps(
            voice_id="tc_60e5426de8b95f1d3000d7b5",
            text="Hello.",
            model="ssfm-v30",
        )
        body = req.model_dump(exclude_none=True)
        assert body["voice_id"] == "tc_60e5426de8b95f1d3000d7b5"
        assert "language" not in body  # exclude_none

    def test_response_parses_both_fixture(self):
        from typecast.models import TTSWithTimestampsResponse

        data = _load("both.json")
        resp = TTSWithTimestampsResponse.model_validate(data)
        assert resp.audio_format in {"wav", "mp3"}
        assert resp.audio_duration > 0
        assert resp.words is not None and len(resp.words) >= 1
        assert resp.characters is not None and len(resp.characters) >= 1

    def test_response_parses_word_only_fixture(self):
        from typecast.models import TTSWithTimestampsResponse

        resp = TTSWithTimestampsResponse.model_validate(_load("word_only.json"))
        assert resp.characters is None
        assert resp.words is not None

    def test_response_parses_char_only_fixture(self):
        from typecast.models import TTSWithTimestampsResponse

        resp = TTSWithTimestampsResponse.model_validate(_load("char_only.json"))
        assert resp.words is None
        assert resp.characters is not None


class TestAudioHelpers:
    def test_audio_bytes_decodes_base64(self):
        from typecast.models import TTSWithTimestampsResponse

        resp = TTSWithTimestampsResponse.model_validate(_load("both.json"))
        b = resp.audio_bytes
        assert isinstance(b, bytes)
        assert len(b) > 0
        if resp.audio_format == "wav":
            assert b[:4] == b"RIFF"

    def test_save_audio_writes_file(self, tmp_path):
        from typecast.models import TTSWithTimestampsResponse

        resp = TTSWithTimestampsResponse.model_validate(_load("both.json"))
        out = tmp_path / "out.wav"
        resp.save_audio(str(out))
        assert out.exists()
        assert out.stat().st_size > 0
        assert out.read_bytes() == resp.audio_bytes
