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
