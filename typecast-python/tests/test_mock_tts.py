
import pytest

from typecast.client import Typecast
from typecast.models import Output, Prompt, TTSRequest, TTSResponse


@pytest.fixture
def typecast_client():
    # Get API key from environment variables
    return Typecast()


class TestMockTTS:
    def test_mock_tts(self, typecast_client, mocker):
        # Arrange
        mock_response = mocker.Mock()
        mock_response.status_code = 200
        mock_response.content = b"mock_audio_data"
        mock_response.headers = {"X-Audio-Duration": "1.5", "Content-Type": "audio/wav"}

        mock_post = mocker.patch.object(
            typecast_client.session, "post", return_value=mock_response
        )

        request = TTSRequest(
            text="Hello, this is a test",
            voice_id="tc_test_voice_id",
            model="ssfm-v21",
            language="eng",
            prompt=Prompt(emotion_preset="happy", emotion_intensity=1.5),
            output=Output(
                volume=80, audio_pitch=5, audio_tempo=1.5, audio_format="mp3"
            ),
            seed=42,
        )

        # Act
        response = typecast_client.text_to_speech(request)

        # Assert
        mock_post.assert_called_once_with(
            f"{typecast_client.host}/v1/text-to-speech",
            json=request.model_dump(exclude_none=True),
        )
        assert isinstance(response, TTSResponse)
        assert response.audio_data == b"mock_audio_data"
        assert response.duration == 1.5
        assert response.format == "wav"
