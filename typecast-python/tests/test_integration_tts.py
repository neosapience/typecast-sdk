import os

import pytest

from typecast.client import Typecast
from typecast.models import TTSRequest, TTSResponse


@pytest.fixture
def typecast_client():
    # Get API key from environment variables
    return Typecast()


@pytest.mark.skipif(
    not os.getenv("TYPECAST_API_KEY"), reason="TYPECAST_API_KEY not set"
)
class TestTTSIntegration:
    def test_text_to_speech_integration(self, typecast_client):
        # Arrange
        # First, get an available voice
        voices = typecast_client.voices(model="ssfm-v21")
        voice = voices[0]

        voice_id = voice.voice_id
        model = voice.model

        request = TTSRequest(
            text="Hello, this is a Typecast test.", voice_id=voice_id, model=model
        )

        # Act
        response = typecast_client.text_to_speech(request)

        # Assert
        assert isinstance(response, TTSResponse)
        assert response.audio_data is not None
        assert len(response.audio_data) > 0
        # assert float(response.duration) > 0
        assert response.format in ["wav", "mp3"]
