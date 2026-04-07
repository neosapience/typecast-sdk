"""Unit tests for AsyncTypecast using aioresponses."""

import pytest
from aioresponses import aioresponses

from typecast.async_client import AsyncTypecast
from typecast.exceptions import (
    BadRequestError,
    InternalServerError,
    NotFoundError,
    PaymentRequiredError,
    RateLimitError,
    TypecastError,
    UnauthorizedError,
    UnprocessableEntityError,
)
from typecast.models import TTSRequest


HOST = "https://dummy.example"


@pytest.fixture
def request_payload():
    return TTSRequest(text="Hi", voice_id="tc_test", model="ssfm-v21")


class TestAsyncTextToSpeech:
    async def test_success_wav(self, request_payload):
        with aioresponses() as m:
            m.post(
                f"{HOST}/v1/text-to-speech",
                status=200,
                body=b"audio",
                headers={"X-Audio-Duration": "1.5", "Content-Type": "audio/wav"},
            )
            async with AsyncTypecast(host=HOST, api_key="key") as client:
                resp = await client.text_to_speech(request_payload)
                assert resp.audio_data == b"audio"
                assert resp.format == "wav"
                assert resp.duration == 1.5

    async def test_success_mp3(self, request_payload):
        with aioresponses() as m:
            m.post(
                f"{HOST}/v1/text-to-speech",
                status=200,
                body=b"audio",
                headers={"X-Audio-Duration": "2.5", "Content-Type": "audio/mp3"},
            )
            async with AsyncTypecast(host=HOST, api_key="key") as client:
                resp = await client.text_to_speech(request_payload)
                assert resp.format == "mp3"
                assert resp.duration == 2.5

    @pytest.mark.parametrize(
        "status,exc_class",
        [
            (400, BadRequestError),
            (401, UnauthorizedError),
            (402, PaymentRequiredError),
            (404, NotFoundError),
            (422, UnprocessableEntityError),
            (429, RateLimitError),
            (500, InternalServerError),
            (503, TypecastError),
        ],
    )
    async def test_error_status_codes(self, request_payload, status, exc_class):
        with aioresponses() as m:
            m.post(f"{HOST}/v1/text-to-speech", status=status, body="error")
            async with AsyncTypecast(host=HOST, api_key="key") as client:
                with pytest.raises(exc_class):
                    await client.text_to_speech(request_payload)

    async def test_session_not_initialized_raises(self, request_payload):
        client = AsyncTypecast(host=HOST, api_key="key")
        with pytest.raises(TypecastError, match="session not initialized"):
            await client.text_to_speech(request_payload)


class TestAsyncVoices:
    async def test_voices_success(self):
        with aioresponses() as m:
            m.get(
                f"{HOST}/v1/voices",
                status=200,
                payload=[
                    {"voice_id": "v1", "voice_name": "V1", "model": "ssfm-v21", "emotions": ["normal"]},
                ],
            )
            async with AsyncTypecast(host=HOST, api_key="key") as client:
                voices = await client.voices()
                assert len(voices) == 1

    async def test_voices_with_model_filter(self):
        with aioresponses() as m:
            m.get(
                f"{HOST}/v1/voices?model=ssfm-v21",
                status=200,
                payload=[],
            )
            async with AsyncTypecast(host=HOST, api_key="key") as client:
                await client.voices(model="ssfm-v21")

    async def test_voices_error(self):
        with aioresponses() as m:
            m.get(f"{HOST}/v1/voices", status=401, body="no key")
            async with AsyncTypecast(host=HOST, api_key="key") as client:
                with pytest.raises(UnauthorizedError):
                    await client.voices()

    async def test_voices_session_not_initialized(self):
        client = AsyncTypecast(host=HOST, api_key="key")
        with pytest.raises(TypecastError, match="session not initialized"):
            await client.voices()


class TestAsyncGetVoice:
    async def test_get_voice_returns_first_of_list(self):
        with aioresponses() as m:
            m.get(
                f"{HOST}/v1/voices/v1",
                status=200,
                payload=[
                    {"voice_id": "v1", "voice_name": "V1", "model": "ssfm-v21", "emotions": ["normal"]},
                ],
            )
            async with AsyncTypecast(host=HOST, api_key="key") as client:
                voice = await client.get_voice("v1")
                assert voice.voice_id == "v1"

    async def test_get_voice_returns_dict_directly(self):
        with aioresponses() as m:
            m.get(
                f"{HOST}/v1/voices/v1",
                status=200,
                payload={"voice_id": "v1", "voice_name": "V1", "model": "ssfm-v21", "emotions": ["normal"]},
            )
            async with AsyncTypecast(host=HOST, api_key="key") as client:
                voice = await client.get_voice("v1")
                assert voice.voice_id == "v1"

    async def test_get_voice_error(self):
        with aioresponses() as m:
            m.get(f"{HOST}/v1/voices/missing", status=404, body="not found")
            async with AsyncTypecast(host=HOST, api_key="key") as client:
                with pytest.raises(NotFoundError):
                    await client.get_voice("missing")

    async def test_get_voice_session_not_initialized(self):
        client = AsyncTypecast(host=HOST, api_key="key")
        with pytest.raises(TypecastError):
            await client.get_voice("v1")


class TestAsyncVoicesV2:
    async def test_no_filter(self):
        with aioresponses() as m:
            m.get(f"{HOST}/v2/voices", status=200, payload=[])
            async with AsyncTypecast(host=HOST, api_key="key") as client:
                result = await client.voices_v2()
                assert result == []

    async def test_with_filter(self):
        from typecast.models.voices import VoicesV2Filter
        with aioresponses() as m:
            m.get(
                f"{HOST}/v2/voices?model=ssfm-v30",
                status=200,
                payload=[],
            )
            async with AsyncTypecast(host=HOST, api_key="key") as client:
                await client.voices_v2(filter=VoicesV2Filter(model="ssfm-v30"))

    async def test_error(self):
        with aioresponses() as m:
            m.get(f"{HOST}/v2/voices", status=500, body="boom")
            async with AsyncTypecast(host=HOST, api_key="key") as client:
                with pytest.raises(InternalServerError):
                    await client.voices_v2()

    async def test_session_not_initialized(self):
        client = AsyncTypecast(host=HOST, api_key="key")
        with pytest.raises(TypecastError):
            await client.voices_v2()


class TestAsyncVoiceV2:
    async def test_success(self):
        with aioresponses() as m:
            m.get(
                f"{HOST}/v2/voices/v1",
                status=200,
                payload={
                    "voice_id": "v1",
                    "voice_name": "V1",
                    "models": [{"version": "ssfm-v30", "emotions": ["normal"]}],
                },
            )
            async with AsyncTypecast(host=HOST, api_key="key") as client:
                voice = await client.voice_v2("v1")
                assert voice.voice_id == "v1"

    async def test_error(self):
        with aioresponses() as m:
            m.get(f"{HOST}/v2/voices/missing", status=404, body="not found")
            async with AsyncTypecast(host=HOST, api_key="key") as client:
                with pytest.raises(NotFoundError):
                    await client.voice_v2("missing")

    async def test_session_not_initialized(self):
        client = AsyncTypecast(host=HOST, api_key="key")
        with pytest.raises(TypecastError):
            await client.voice_v2("v1")


class TestAsyncContextManager:
    async def test_aexit_with_session(self):
        async with AsyncTypecast(host=HOST, api_key="key") as client:
            assert client.session is not None

    async def test_init_without_api_key_still_constructs(self):
        client = AsyncTypecast(host=HOST, api_key="some-key")
        assert client.api_key == "some-key"
