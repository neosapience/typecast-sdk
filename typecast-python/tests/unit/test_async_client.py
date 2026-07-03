"""Unit tests for AsyncTypecast using aioresponses."""

import aiohttp
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
from typecast.models import Output, OutputStream, TTSModel, TTSRequest, TTSRequestStream


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

    async def test_generate_to_file_defaults_model_and_infers_mp3(self, tmp_path):
        with aioresponses() as m:
            m.post(
                f"{HOST}/v1/text-to-speech",
                status=200,
                body=b"audio",
                headers={"X-Audio-Duration": "2.5", "Content-Type": "audio/mp3"},
            )
            output_path = tmp_path / "speech.mp3"
            async with AsyncTypecast(host=HOST, api_key="key") as client:
                resp = await client.generate_to_file(
                    output_path,
                    text="Hi",
                    voice_id="tc_test",
                )
                assert resp.format == "mp3"
                assert output_path.read_bytes() == b"audio"

            sent = next(iter(m.requests.values()))[0].kwargs["json"]
            assert sent["model"] == "ssfm-v30"
            assert sent["output"]["audio_format"] == "mp3"

    async def test_generate_to_file_keeps_explicit_output(self, tmp_path):
        with aioresponses() as m:
            m.post(f"{HOST}/v1/text-to-speech", status=200, body=b"audio", repeat=True)
            async with AsyncTypecast(host=HOST, api_key="key") as client:
                await client.generate_to_file(
                    tmp_path / "speech.mp3",
                    text="Hi",
                    voice_id="tc_test",
                    output=Output(audio_format="wav"),
                )
                await client.generate_to_file(
                    tmp_path / "speech",
                    text="Hi",
                    voice_id="tc_test",
                )
                await client.generate_to_file(
                    tmp_path / "speech.mp3",
                    text="Hi",
                    voice_id="tc_test",
                    output=Output(audio_format=None),
                )

            requests = next(iter(m.requests.values()))
            first = requests[0].kwargs["json"]
            second = requests[1].kwargs["json"]
            third = requests[2].kwargs["json"]
            assert first["output"]["audio_format"] == "wav"
            assert "output" not in second
            assert third["output"]["audio_format"] == "mp3"

    async def test_generate_to_file_rejects_blank_path_before_request(self):
        with aioresponses() as m:
            async with AsyncTypecast(host=HOST, api_key="key") as client:
                with pytest.raises(ValueError, match="path cannot be empty"):
                    await client.generate_to_file("   ", text="Hi", voice_id="tc_test")

            assert not m.requests

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
                    {
                        "voice_id": "v1",
                        "voice_name": "V1",
                        "model": "ssfm-v21",
                        "emotions": ["normal"],
                    },
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
                    {
                        "voice_id": "v1",
                        "voice_name": "V1",
                        "model": "ssfm-v21",
                        "emotions": ["normal"],
                    },
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
                payload={
                    "voice_id": "v1",
                    "voice_name": "V1",
                    "model": "ssfm-v21",
                    "emotions": ["normal"],
                },
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


class TestAsyncRecommendVoices:
    async def test_success(self):
        with aioresponses() as m:
            m.get(
                f"{HOST}/v1/voices/recommendations?query=warm+narrator&count=2",
                status=200,
                payload=[
                    {"voice_id": "v1", "voice_name": "Voice 1", "score": 0.97},
                ],
            )
            async with AsyncTypecast(host=HOST, api_key="key") as client:
                voices = await client.recommend_voices("warm narrator", count=2)
                assert len(voices) == 1
                assert voices[0].voice_id == "v1"
                assert voices[0].score == 0.97

    async def test_default_count_and_validation(self):
        with aioresponses() as m:
            m.get(
                f"{HOST}/v1/voices/recommendations?query=voice&count=5",
                status=200,
                payload=[],
            )
            async with AsyncTypecast(host=HOST, api_key="key") as client:
                await client.recommend_voices("voice")
                with pytest.raises(ValueError, match="count must be between 1 and 10"):
                    await client.recommend_voices("voice", count=11)

    async def test_session_not_initialized(self):
        client = AsyncTypecast(host=HOST, api_key="key")
        with pytest.raises(TypecastError):
            await client.recommend_voices("voice")

    async def test_error(self):
        with aioresponses() as m:
            m.get(
                f"{HOST}/v1/voices/recommendations?query=voice&count=5",
                status=401,
                body="no key",
            )
            async with AsyncTypecast(host=HOST, api_key="key") as client:
                with pytest.raises(UnauthorizedError):
                    await client.recommend_voices("voice")


class TestAsyncContextManager:
    async def test_aexit_with_session(self):
        async with AsyncTypecast(host=HOST, api_key="key") as client:
            assert client.session is not None

    async def test_default_host_without_api_key_raises(self, monkeypatch):
        monkeypatch.delenv("TYPECAST_API_KEY", raising=False)
        with pytest.raises(ValueError, match="API key is required"):
            AsyncTypecast()

    async def test_init_without_api_key_still_constructs(self):
        client = AsyncTypecast(host=HOST, api_key="some-key")
        assert client.api_key == "some-key"

    async def test_aenter_without_api_key_omits_x_api_key_header(self, monkeypatch):
        """When no api_key is provided (and no env var), __aenter__ skips
        the X-API-KEY header — covers the falsy branch of the if guard."""
        monkeypatch.delenv("TYPECAST_API_KEY", raising=False)
        async with AsyncTypecast(host=HOST) as client:
            assert client.session is not None
            # The session was created without the X-API-KEY header
            assert "X-API-KEY" not in client.session._default_headers
            user_agent = client.session._default_headers["User-Agent"]
            assert user_agent.startswith("typecast-python/")
            assert "aiohttp/" in user_agent
            assert "mode=async; base=custom; transport=rest" in user_agent
            assert "os=" in user_agent
            assert "arch=" in user_agent
            assert "sdk_env=python; platform=server" in user_agent

    async def test_aexit_without_session_is_noop(self):
        """Calling __aexit__ without ever calling __aenter__ should not
        raise — covers the falsy branch of the if self.session: guard."""
        client = AsyncTypecast(host=HOST, api_key="key")
        assert client.session is None
        await client.__aexit__(None, None, None)


class TestAsyncTextToSpeechStream:
    @pytest.fixture
    def stream_request(self):
        return TTSRequestStream(
            voice_id="tc_test",
            text="Hi",
            model="ssfm-v30",
            output=OutputStream(audio_format="wav"),
        )

    async def test_stream_yields_chunks(self, stream_request):
        # aioresponses returns the body in a single chunk; iter_chunked
        # then re-slices it. We just verify all bytes round-trip.
        body = b"RIFF\xff\xff\xff\xffWAVEfmt PCM_DATA_BLOCK"
        with aioresponses() as m:
            m.post(
                f"{HOST}/v1/text-to-speech/stream",
                status=200,
                body=body,
            )
            async with AsyncTypecast(host=HOST, api_key="key") as client:
                collected = b""
                async for chunk in client.text_to_speech_stream(
                    stream_request, chunk_size=4
                ):
                    collected += chunk
                assert collected == body

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
    async def test_stream_error_status_codes(self, stream_request, status, exc_class):
        with aioresponses() as m:
            m.post(
                f"{HOST}/v1/text-to-speech/stream",
                status=status,
                body=f"err {status}",
            )
            async with AsyncTypecast(host=HOST, api_key="key") as client:
                with pytest.raises(exc_class):
                    async for _ in client.text_to_speech_stream(stream_request):
                        pass

    async def test_stream_session_not_initialized(self, stream_request):
        client = AsyncTypecast(host=HOST, api_key="key")
        with pytest.raises(TypecastError, match="session not initialized"):
            async for _ in client.text_to_speech_stream(stream_request):
                pass


class TestAsyncStreamChunkSizeValidation:
    async def test_zero_chunk_size_raises(self):
        stream_request = TTSRequestStream(voice_id="tc_x", text="hi", model="ssfm-v30")
        async with AsyncTypecast(host=HOST, api_key="key") as client:
            with pytest.raises(
                ValueError, match="chunk_size must be a positive integer"
            ):
                async for _ in client.text_to_speech_stream(
                    stream_request, chunk_size=0
                ):
                    pass

    async def test_negative_chunk_size_raises(self):
        stream_request = TTSRequestStream(voice_id="tc_x", text="hi", model="ssfm-v30")
        async with AsyncTypecast(host=HOST, api_key="key") as client:
            with pytest.raises(
                ValueError, match="chunk_size must be a positive integer"
            ):
                async for _ in client.text_to_speech_stream(
                    stream_request, chunk_size=-1
                ):
                    pass

    async def test_bool_chunk_size_raises(self):
        stream_request = TTSRequestStream(voice_id="tc_x", text="hi", model="ssfm-v30")
        async with AsyncTypecast(host=HOST, api_key="key") as client:
            with pytest.raises(
                ValueError, match="chunk_size must be a positive integer"
            ):
                async for _ in client.text_to_speech_stream(
                    stream_request, chunk_size=True
                ):
                    pass


class TestAsyncSubscription:
    PAYLOAD = {
        "plan": "plus",
        "credits": {"plan_credits": 100000, "used_credits": 1234},
        "limits": {"concurrency_limit": 5},
    }

    async def test_get_my_subscription_success(self):
        from typecast.models import PlanTier

        with aioresponses() as m:
            m.get(
                f"{HOST}/v1/users/me/subscription",
                status=200,
                payload=self.PAYLOAD,
            )
            async with AsyncTypecast(host=HOST, api_key="key") as client:
                sub = await client.get_my_subscription()
                assert sub.plan == PlanTier.PLUS
                assert sub.credits.plan_credits == 100000
                assert sub.credits.used_credits == 1234
                assert sub.limits.concurrency_limit == 5

    @pytest.mark.parametrize(
        "status,exc_class",
        [
            (401, UnauthorizedError),
            (429, RateLimitError),
            (500, InternalServerError),
        ],
    )
    async def test_get_my_subscription_errors(self, status, exc_class):
        with aioresponses() as m:
            m.get(
                f"{HOST}/v1/users/me/subscription",
                status=status,
                body=f"err {status}",
            )
            async with AsyncTypecast(host=HOST, api_key="key") as client:
                with pytest.raises(exc_class):
                    await client.get_my_subscription()

    async def test_get_my_subscription_session_not_initialized(self):
        client = AsyncTypecast(host=HOST, api_key="key")
        with pytest.raises(TypecastError, match="session not initialized"):
            await client.get_my_subscription()


@pytest.mark.asyncio
async def test_external_session_is_not_recreated():
    """An injected external aiohttp.ClientSession is not replaced by __aenter__."""
    external = aiohttp.ClientSession()
    try:
        async with AsyncTypecast(host=HOST, api_key="key", session=external) as client:
            assert client.session is external
        assert not external.closed
    finally:
        if not external.closed:
            await external.close()


@pytest.mark.asyncio
async def test_owned_session_is_closed_on_exit():
    """An owned session is closed by __aexit__."""
    async with AsyncTypecast(host=HOST, api_key="key") as client:
        session = client.session
        assert session is not None
    assert session.closed


@pytest.mark.asyncio
async def test_external_session_can_reenter():
    """An external-session client can be re-entered; re-entry reuses the injected session."""
    external = aiohttp.ClientSession()
    try:
        client = AsyncTypecast(host=HOST, api_key="key", session=external)
        async with client:
            assert client.session is external
        # Re-enter the same client; the injected session must be reused, not replaced.
        async with client:
            assert client.session is external
        assert not external.closed
    finally:
        if not external.closed:
            await external.close()


@pytest.mark.asyncio
async def test_external_session_sends_per_request_auth_header():
    """When using an external session, each request carries the X-API-KEY header per-request."""
    external = aiohttp.ClientSession()
    try:
        with aioresponses() as m:
            m.post(f"{HOST}/v1/text-to-speech", status=200, body=b"", repeat=True)
            async with AsyncTypecast(host=HOST, api_key="key", session=external) as client:
                request = TTSRequest(
                    text="hi",
                    voice_id="tc_62a8975e695ad26f7fb514d1",
                    model=TTSModel.SSFM_V21,
                )
                await client.text_to_speech(request)
            for (method, url), req_list in m.requests.items():
                for req in req_list:
                    headers = req.kwargs.get("headers") or {}
                    assert headers.get("X-API-KEY") == "key", (
                        f"per-request X-API-KEY missing on {method} {url}"
                    )
                    assert headers.get("User-Agent"), (
                        f"per-request User-Agent missing on {method} {url}"
                    )
    finally:
        if not external.closed:
            await external.close()


@pytest.mark.asyncio
async def test_external_session_without_api_key_omits_x_api_key_header():
    """External session + non-default host + no api_key: per-request headers omit X-API-KEY.

    Covers the falsy branch of ``if self.api_key:`` in ``_request_headers()``
    for the external-session path (``self._owns_session`` is False).
    """
    external = aiohttp.ClientSession()
    try:
        async with AsyncTypecast(
            host="https://custom.example.com", api_key=None, session=external
        ) as client:
            headers = client._request_headers()
            assert headers is not None, "external session should return per-request headers"
            assert "X-API-KEY" not in headers
            assert headers.get("User-Agent"), "User-Agent should be present"
    finally:
        if not external.closed:
            await external.close()
