import os

import aiohttp
import pytest

from typecast.async_client import AsyncTypecast
from typecast.models import (
    LanguageCode,
    TTSModel,
    TTSRequest,
    TTSRequestStream,
    TTSResponse,
    VoicesResponse,
)

# Dev API host. The dev key (TYPECAST_GLOBALAPI_KEY_HAMIN_DEV) is used for E2E
# tests against the real dev environment. Voice IDs differ between dev and prod,
# so tests targeting dev fetch a voice dynamically from the dev API.
DEV_HOST = "https://api.icepeak.in"


@pytest.mark.asyncio
@pytest.mark.skipif(
    not os.getenv("TYPECAST_API_KEY"), reason="TYPECAST_API_KEY not set"
)
class TestAsyncClient:
    async def test_async_text_to_speech(self):
        """Test async text-to-speech with real API"""
        async with AsyncTypecast() as cli:
            # Get a voice first
            voices = await cli.voices(model="ssfm-v21")
            assert len(voices) > 0

            # Create request
            request = TTSRequest(
                text="Hello, this is an async test.",
                voice_id=voices[0].voice_id,
                model="ssfm-v21",
                language=LanguageCode.ENG,
            )

            # Generate audio
            response = await cli.text_to_speech(request)

            # Assert
            assert isinstance(response, TTSResponse)
            assert response.audio_data is not None
            assert len(response.audio_data) > 0
            assert response.format in ["wav", "mp3"]

    async def test_async_voices(self):
        """Test async voices listing"""
        async with AsyncTypecast() as cli:
            voices = await cli.voices()

            assert isinstance(voices, list)
            assert len(voices) > 0
            assert all(isinstance(v, VoicesResponse) for v in voices)

    async def test_async_voices_with_model_filter(self):
        """Test async voices with model filter"""
        async with AsyncTypecast() as cli:
            target_model = "ssfm-v21"
            voices = await cli.voices(model=target_model)

            assert isinstance(voices, list)
            assert len(voices) > 0
            assert all(v.model == target_model for v in voices)

    async def test_async_get_voice(self):
        """Test async get specific voice"""
        async with AsyncTypecast() as cli:
            # Get a voice first
            voices = await cli.voices()
            assert len(voices) > 0
            voice_id = voices[0].voice_id

            # Get specific voice
            voice = await cli.get_voice(voice_id)

            assert isinstance(voice, VoicesResponse)
            assert voice.voice_id == voice_id
            assert voice.voice_name
            assert voice.model
            assert voice.emotions


@pytest.mark.e2e
@pytest.mark.asyncio
async def test_stream_with_external_session_real_dev():
    """E2E: external-session streaming against the real dev API.

    Verifies that streaming with an externally-injected aiohttp.ClientSession
    works against the real dev API, and that the client does NOT close the
    caller-owned session (the core A1/A3 behavior).
    """
    api_key = os.getenv("TYPECAST_GLOBALAPI_KEY_HAMIN_DEV")
    if not api_key:
        pytest.skip("TYPECAST_GLOBALAPI_KEY_HAMIN_DEV not set")
    external = aiohttp.ClientSession()
    try:
        async with AsyncTypecast(
            host=DEV_HOST, api_key=api_key, session=external
        ) as client:
            # Voice IDs differ per environment; pick one available on dev.
            voices = await client.voices(model="ssfm-v30")
            assert voices, "no voices returned from dev API"
            dev_voice_id = voices[0].voice_id

            req = TTSRequestStream(
                text="안녕하세요.",
                voice_id=dev_voice_id,
                model=TTSModel.SSFM_V30,
            )
            chunks = [c async for c in client.text_to_speech_stream(req)]
        assert not external.closed, "external session must not be closed by the client"
        assert len(chunks) >= 1
        assert chunks[0][:4] == b"RIFF", f"first chunk should be WAV, got {chunks[0][:4]!r}"
    finally:
        if not external.closed:
            await external.close()
