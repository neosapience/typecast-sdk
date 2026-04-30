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


EXPECTED_DIR = FIXTURE_DIR / "expected"


def _load_text(name: str) -> str:
    # Read with binary mode then decode to keep LF intact
    return (EXPECTED_DIR / name).read_bytes().decode("utf-8")


class TestToSrt:
    @pytest.mark.parametrize("fixture", ["both", "word_only", "char_only", "jpn_char"])
    def test_to_srt_matches_expected(self, fixture):
        from typecast.models import TTSWithTimestampsResponse

        resp = TTSWithTimestampsResponse.model_validate(_load(f"{fixture}.json"))
        actual = resp.to_srt()
        expected = _load_text(f"{fixture}.srt")
        assert actual == expected, (
            f"SRT byte mismatch for {fixture}\n"
            f"--- expected (len={len(expected)}) ---\n{expected[:200]!r}\n"
            f"--- actual   (len={len(actual)}) ---\n{actual[:200]!r}"
        )

    def test_to_srt_uses_characters_when_words_collapsed(self):
        """If words has only one segment but characters has multiple,
        captioning must drop to character granularity. Covers jpn/zho path."""
        from typecast.models import (
            TTSWithTimestampsResponse,
            AlignmentSegmentWord,
            AlignmentSegmentCharacter,
        )

        resp = TTSWithTimestampsResponse(
            audio="UklGRgAAAA==",
            audio_format="wav",
            audio_duration=1.0,
            words=[AlignmentSegmentWord(text="こんにちは。お元気ですか?", start=0.0, end=1.0)],
            characters=[
                AlignmentSegmentCharacter(text="こ", start=0.0, end=0.1),
                AlignmentSegmentCharacter(text="ん", start=0.1, end=0.2),
                AlignmentSegmentCharacter(text="に", start=0.2, end=0.3),
                AlignmentSegmentCharacter(text="ち", start=0.3, end=0.4),
                AlignmentSegmentCharacter(text="は", start=0.4, end=0.5),
                AlignmentSegmentCharacter(text="。", start=0.5, end=0.6),
            ],
        )
        srt = resp.to_srt()
        # First cue must end at the period (0.6s) because '。' triggers a split
        assert "00:00:00,600" in srt
        assert srt.count("\n\n") >= 1  # at least one cue boundary

    def test_to_srt_raises_when_both_arrays_empty(self):
        from typecast.models import TTSWithTimestampsResponse

        resp = TTSWithTimestampsResponse(
            audio="UklGRgAAAA==", audio_format="wav", audio_duration=0.0,
            words=None, characters=None,
        )
        with pytest.raises(ValueError, match="no alignment segments"):
            resp.to_srt()


class TestToVtt:
    @pytest.mark.parametrize("fixture", ["both", "word_only", "char_only", "jpn_char"])
    def test_to_vtt_matches_expected(self, fixture):
        from typecast.models import TTSWithTimestampsResponse

        resp = TTSWithTimestampsResponse.model_validate(_load(f"{fixture}.json"))
        actual = resp.to_vtt()
        expected = _load_text(f"{fixture}.vtt")
        assert actual == expected, (
            f"VTT byte mismatch for {fixture}\n"
            f"--- expected (len={len(expected)}) ---\n{expected[:200]!r}\n"
            f"--- actual   (len={len(actual)}) ---\n{actual[:200]!r}"
        )

    def test_to_vtt_starts_with_header(self):
        from typecast.models import TTSWithTimestampsResponse

        resp = TTSWithTimestampsResponse.model_validate(_load("both.json"))
        out = resp.to_vtt()
        assert out.startswith("WEBVTT\n\n")

    def test_to_vtt_uses_dot_in_timestamps(self):
        from typecast.models import TTSWithTimestampsResponse

        resp = TTSWithTimestampsResponse.model_validate(_load("both.json"))
        out = resp.to_vtt()
        # VTT uses dot; SRT uses comma. Make sure we have dot in timestamps.
        lines = out.split("\n")
        timestamp_lines = [line for line in lines if " --> " in line]
        assert len(timestamp_lines) > 0
        for ts_line in timestamp_lines:
            # Extract times before -->
            before_arrow = ts_line.split(" --> ")[0]
            assert "." in before_arrow
            assert "," not in before_arrow


class TestSyncClient:
    def test_calls_endpoint_with_no_granularity(self, mocker):
        from typecast.client import Typecast
        from typecast.models import TTSRequestWithTimestamps, TTSWithTimestampsResponse

        client = Typecast(api_key="test-key")
        fixture = _load("both.json")
        mock_resp = mocker.Mock(status_code=200, text="", headers={})
        mock_resp.json = mocker.Mock(return_value=fixture)
        mock_post = mocker.patch.object(client.session, "post", return_value=mock_resp)

        req = TTSRequestWithTimestamps(
            voice_id="tc_x", text="Hello.", model="ssfm-v30", language="eng"
        )
        out = client.text_to_speech_with_timestamps(req)

        mock_post.assert_called_once()
        called_url = mock_post.call_args.args[0]
        assert called_url == f"{client.host}/v1/text-to-speech/with-timestamps"
        assert "params" not in mock_post.call_args.kwargs or not mock_post.call_args.kwargs.get("params")
        assert mock_post.call_args.kwargs.get("timeout") == (10, 300)
        assert isinstance(out, TTSWithTimestampsResponse)
        assert out.audio_format in {"wav", "mp3"}

    def test_passes_granularity_query(self, mocker):
        from typecast.client import Typecast
        from typecast.models import TTSRequestWithTimestamps

        client = Typecast(api_key="test-key")
        fixture = _load("word_only.json")
        mock_resp = mocker.Mock(status_code=200, text="", headers={})
        mock_resp.json = mocker.Mock(return_value=fixture)
        mock_post = mocker.patch.object(client.session, "post", return_value=mock_resp)

        req = TTSRequestWithTimestamps(voice_id="tc_x", text="Hello.", model="ssfm-v30")
        client.text_to_speech_with_timestamps(req, granularity="word")
        assert mock_post.call_args.kwargs["params"] == {"granularity": "word"}

    def test_rejects_invalid_granularity(self):
        from typecast.client import Typecast
        from typecast.models import TTSRequestWithTimestamps

        client = Typecast(api_key="test-key")
        req = TTSRequestWithTimestamps(voice_id="tc_x", text="Hi", model="ssfm-v30")
        with pytest.raises(ValueError, match="granularity"):
            client.text_to_speech_with_timestamps(req, granularity="words")  # type: ignore[arg-type]

    def test_handles_402_error(self, mocker):
        from typecast.client import Typecast
        from typecast.exceptions import PaymentRequiredError
        from typecast.models import TTSRequestWithTimestamps

        client = Typecast(api_key="test-key")
        mock_resp = mocker.Mock(status_code=402, text="Insufficient credit")
        mocker.patch.object(client.session, "post", return_value=mock_resp)

        req = TTSRequestWithTimestamps(voice_id="tc_x", text="Hi", model="ssfm-v30")
        with pytest.raises(PaymentRequiredError):
            client.text_to_speech_with_timestamps(req)


class TestAsyncClient:
    @pytest.mark.asyncio
    async def test_async_calls_endpoint_no_granularity(self, mocker):
        """Async client returns parsed response when granularity is omitted."""
        from typecast.async_client import AsyncTypecast
        from typecast.models import TTSRequestWithTimestamps, TTSWithTimestampsResponse

        async with AsyncTypecast(api_key="test-key") as client:
            fixture = _load("both.json")
            mock_resp = mocker.AsyncMock()
            mock_resp.status = 200
            mock_resp.json = mocker.AsyncMock(return_value=fixture)
            mock_resp.text = mocker.AsyncMock(return_value="")

            mock_cm = mocker.MagicMock()
            mock_cm.__aenter__ = mocker.AsyncMock(return_value=mock_resp)
            mock_cm.__aexit__ = mocker.AsyncMock(return_value=None)
            mock_post = mocker.patch.object(client.session, "post", return_value=mock_cm)

            req = TTSRequestWithTimestamps(voice_id="tc_x", text="Hi", model="ssfm-v30")
            out = await client.text_to_speech_with_timestamps(req)

            mock_post.assert_called_once()
            called_url = mock_post.call_args.args[0]
            assert called_url == f"{client.host}/v1/text-to-speech/with-timestamps"
            kwargs = mock_post.call_args.kwargs
            assert kwargs.get("params") is None
            assert isinstance(out, TTSWithTimestampsResponse)

    @pytest.mark.asyncio
    async def test_async_passes_granularity_query(self, mocker):
        from typecast.async_client import AsyncTypecast
        from typecast.models import TTSRequestWithTimestamps

        async with AsyncTypecast(api_key="test-key") as client:
            fixture = _load("word_only.json")
            mock_resp = mocker.AsyncMock()
            mock_resp.status = 200
            mock_resp.json = mocker.AsyncMock(return_value=fixture)
            mock_resp.text = mocker.AsyncMock(return_value="")

            mock_cm = mocker.MagicMock()
            mock_cm.__aenter__ = mocker.AsyncMock(return_value=mock_resp)
            mock_cm.__aexit__ = mocker.AsyncMock(return_value=None)
            mock_post = mocker.patch.object(client.session, "post", return_value=mock_cm)

            req = TTSRequestWithTimestamps(voice_id="tc_x", text="Hi", model="ssfm-v30")
            await client.text_to_speech_with_timestamps(req, granularity="word")
            assert mock_post.call_args.kwargs["params"] == {"granularity": "word"}

    @pytest.mark.asyncio
    async def test_async_rejects_invalid_granularity(self):
        from typecast.async_client import AsyncTypecast
        from typecast.models import TTSRequestWithTimestamps

        async with AsyncTypecast(api_key="test-key") as client:
            req = TTSRequestWithTimestamps(voice_id="tc_x", text="Hi", model="ssfm-v30")
            with pytest.raises(ValueError, match="granularity"):
                await client.text_to_speech_with_timestamps(req, granularity="words")  # type: ignore[arg-type]

    @pytest.mark.asyncio
    async def test_async_raises_when_session_not_initialized(self):
        from typecast.async_client import AsyncTypecast
        from typecast.exceptions import TypecastError
        from typecast.models import TTSRequestWithTimestamps

        client = AsyncTypecast(api_key="test-key")
        # No `async with`, so session is None
        req = TTSRequestWithTimestamps(voice_id="tc_x", text="Hi", model="ssfm-v30")
        with pytest.raises(TypecastError, match="session"):
            await client.text_to_speech_with_timestamps(req)

    @pytest.mark.asyncio
    async def test_async_error_path_handles_402(self, mocker):
        """Async client maps non-200 status to specific exception (covers async_client.py:210-211)."""
        from typecast.async_client import AsyncTypecast
        from typecast.exceptions import PaymentRequiredError
        from typecast.models import TTSRequestWithTimestamps

        async with AsyncTypecast(api_key="k") as client:
            mock_resp = mocker.AsyncMock()
            mock_resp.status = 402
            mock_resp.text = mocker.AsyncMock(return_value="Insufficient credit")
            mock_cm = mocker.MagicMock()
            mock_cm.__aenter__ = mocker.AsyncMock(return_value=mock_resp)
            mock_cm.__aexit__ = mocker.AsyncMock(return_value=None)
            mocker.patch.object(client.session, "post", return_value=mock_cm)

            req = TTSRequestWithTimestamps(voice_id="tc_x", text="Hi", model="ssfm-v30")
            with pytest.raises(PaymentRequiredError):
                await client.text_to_speech_with_timestamps(req)


class TestCoverageGapsTimestamps:
    def test_to_srt_with_single_word_no_characters(self):
        """Covers _segments_for_captioning line 276: 1 word + characters=None."""
        from typecast.models import TTSWithTimestampsResponse, AlignmentSegmentWord

        resp = TTSWithTimestampsResponse(
            audio="UklGRgAAAA==", audio_format="wav", audio_duration=1.0,
            words=[AlignmentSegmentWord(text="Hello.", start=0.0, end=1.0)],
            characters=None,
        )
        out = resp.to_srt()
        assert "Hello." in out
        assert "00:00:00,000 --> 00:00:01,000" in out

    def test_to_srt_handles_segments_without_terminator(self):
        """Covers _group_into_cues line 323 (leftover parts at end of loop)."""
        from typecast.models import TTSWithTimestampsResponse, AlignmentSegmentWord

        # Two short word segments, neither ends in sentence terminator and total stays under 7s/42 chars
        resp = TTSWithTimestampsResponse(
            audio="UklGRgAAAA==", audio_format="wav", audio_duration=1.0,
            words=[
                AlignmentSegmentWord(text="Hello", start=0.0, end=0.5),
                AlignmentSegmentWord(text="world", start=0.5, end=1.0),
            ],
            characters=None,
        )
        out = resp.to_srt()
        assert "Hello world" in out  # joined as a single cue via leftover-flush path
        assert "00:00:00,000 --> 00:00:01,000" in out

    def test_to_srt_raises_when_segments_yield_empty_cues(self):
        """Covers tts.py line 386 + 301->exit: every segment text is empty after strip."""
        from typecast.models import TTSWithTimestampsResponse, AlignmentSegmentCharacter

        resp = TTSWithTimestampsResponse(
            audio="UklGRgAAAA==", audio_format="wav", audio_duration=1.0,
            words=None,
            characters=[
                AlignmentSegmentCharacter(text="", start=0.0, end=0.5),
                AlignmentSegmentCharacter(text="", start=0.5, end=1.0),
            ],
        )
        with pytest.raises(ValueError, match="no alignment segments"):
            resp.to_srt()

    def test_to_vtt_raises_when_segments_yield_empty_cues(self):
        """Covers tts.py line 406."""
        from typecast.models import TTSWithTimestampsResponse, AlignmentSegmentCharacter

        resp = TTSWithTimestampsResponse(
            audio="UklGRgAAAA==", audio_format="wav", audio_duration=1.0,
            words=None,
            characters=[
                AlignmentSegmentCharacter(text="", start=0.0, end=0.5),
            ],
        )
        with pytest.raises(ValueError, match="no alignment segments"):
            resp.to_vtt()

    def test_hard_cap_flushes_before_exceeding_char_limit(self):
        """Covers _group_into_cues lines 314-316: hard-cap flush before appending."""
        from typecast.models import TTSWithTimestampsResponse, AlignmentSegmentWord

        # Build a response with enough words to hit the 42-char hard cap on
        # the fourth word being added.  Each word is 10 chars, so 4 words would
        # give "word01234 word01234 word01234 word01234" = 39 chars (under cap).
        # Use 14-char words: 3 × "word0123456789" = 44 chars >= 42 — flush fires.
        resp = TTSWithTimestampsResponse(
            audio="UklGRgAAAA==", audio_format="wav", audio_duration=5.0,
            words=[
                AlignmentSegmentWord(text="word0123456789", start=0.0, end=1.0),
                AlignmentSegmentWord(text="word0123456789", start=1.0, end=2.0),
                AlignmentSegmentWord(text="word0123456789", start=2.0, end=3.0),
            ],
            characters=None,
        )
        out = resp.to_srt()
        # The hard cap must have split: first two words (29 chars with space) fit,
        # adding a third would make 44 chars >= 42, so third word becomes its own cue.
        assert out.count("\n\n") >= 2  # at least two cues separated by blank line
