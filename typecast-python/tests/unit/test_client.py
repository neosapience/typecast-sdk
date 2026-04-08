
import pytest
from pydantic import ValidationError

from typecast.client import Typecast
from typecast.models import (
    Output,
    OutputStream,
    Prompt,
    TTSRequest,
    TTSRequestStream,
    TTSResponse,
)


@pytest.fixture
def typecast_client():
    # Get API key from environment variables
    return Typecast()


class TestOutputValidation:
    def test_target_lufs_valid(self):
        output = Output(target_lufs=-14.0)
        assert output.target_lufs == -14.0
        assert output.volume is None

    def test_target_lufs_range(self):
        with pytest.raises(ValidationError):
            Output(volume=None, target_lufs=-71.0)
        with pytest.raises(ValidationError):
            Output(volume=None, target_lufs=1.0)

    def test_volume_and_target_lufs_mutual_exclusion(self):
        with pytest.raises(ValueError):
            Output(volume=100, target_lufs=-14.0)

    def test_target_lufs_with_explicit_volume_none(self):
        output = Output(volume=None, target_lufs=-14.0)
        assert output.volume is None
        assert output.target_lufs == -14.0

    def test_validator_passthrough_with_non_dict_input(self):
        """The check_volume_and_target_lufs validator must return non-dict
        input unchanged. Covers the falsy branch of `isinstance(data, dict)`.
        Pydantic before-mode validators are invoked with raw input which is
        not always a dict — when pydantic passes a model instance directly
        (e.g. during revalidation), the validator must pass it through."""
        sentinel = "not-a-dict"
        result = Output.check_volume_and_target_lufs(sentinel)
        assert result == sentinel


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


class TestSyncClient:
    @pytest.fixture
    def client(self):
        return Typecast(host="https://dummy.example", api_key="test-key")

    @pytest.fixture
    def sample_request(self):
        return TTSRequest(text="Hello", voice_id="tc_test", model="ssfm-v21")

    def _mock_response(self, mocker, status_code=200, content=b"data", headers=None, json_data=None, text=""):
        m = mocker.Mock()
        m.status_code = status_code
        m.content = content
        m.headers = headers or {"X-Audio-Duration": "1.5", "Content-Type": "audio/wav"}
        m.text = text
        if json_data is not None:
            m.json.return_value = json_data
        return m

    def test_text_to_speech_success_wav(self, client, sample_request, mocker):
        mock_resp = self._mock_response(mocker)
        mocker.patch.object(client.session, "post", return_value=mock_resp)
        response = client.text_to_speech(sample_request)
        assert response.audio_data == b"data"
        assert response.format == "wav"

    def test_text_to_speech_success_mp3(self, client, sample_request, mocker):
        mock_resp = self._mock_response(mocker, headers={"X-Audio-Duration": "2.0", "Content-Type": "audio/mp3"})
        mocker.patch.object(client.session, "post", return_value=mock_resp)
        response = client.text_to_speech(sample_request)
        assert response.format == "mp3"

    @pytest.mark.parametrize("status,exc_name", [
        (400, "BadRequestError"), (401, "UnauthorizedError"), (402, "PaymentRequiredError"),
        (404, "NotFoundError"), (422, "UnprocessableEntityError"), (429, "RateLimitError"),
        (500, "InternalServerError"), (503, "TypecastError"),
    ])
    def test_text_to_speech_error_status_codes(self, client, sample_request, mocker, status, exc_name):
        from typecast import exceptions as exc_mod
        exc_class = getattr(exc_mod, exc_name)
        mock_resp = self._mock_response(mocker, status_code=status, text=f"error {status}")
        mocker.patch.object(client.session, "post", return_value=mock_resp)
        with pytest.raises(exc_class):
            client.text_to_speech(sample_request)

    def test_voices_success_no_filter(self, client, mocker):
        mock_resp = self._mock_response(mocker, json_data=[{"voice_id": "v1", "voice_name": "Voice 1", "model": "ssfm-v21", "emotions": ["normal"]}])
        get_mock = mocker.patch.object(client.session, "get", return_value=mock_resp)
        voices = client.voices()
        assert len(voices) == 1
        assert voices[0].voice_id == "v1"
        get_mock.assert_called_once_with(f"{client.host}/v1/voices", params={})

    def test_voices_with_model_filter(self, client, mocker):
        mock_resp = self._mock_response(mocker, json_data=[])
        get_mock = mocker.patch.object(client.session, "get", return_value=mock_resp)
        client.voices(model="ssfm-v21")
        get_mock.assert_called_once_with(f"{client.host}/v1/voices", params={"model": "ssfm-v21"})

    def test_voices_error_path(self, client, mocker):
        from typecast.exceptions import UnauthorizedError
        mock_resp = self._mock_response(mocker, status_code=401, text="no key")
        mocker.patch.object(client.session, "get", return_value=mock_resp)
        with pytest.raises(UnauthorizedError):
            client.voices()

    def test_get_voice_returns_first_when_list(self, client, mocker):
        mock_resp = self._mock_response(mocker, json_data=[{"voice_id": "v1", "voice_name": "V1", "model": "ssfm-v21", "emotions": ["normal"]}])
        mocker.patch.object(client.session, "get", return_value=mock_resp)
        voice = client.get_voice("v1")
        assert voice.voice_id == "v1"

    def test_get_voice_returns_dict_when_single(self, client, mocker):
        mock_resp = self._mock_response(mocker, json_data={"voice_id": "v1", "voice_name": "V1", "model": "ssfm-v21", "emotions": ["normal"]})
        mocker.patch.object(client.session, "get", return_value=mock_resp)
        voice = client.get_voice("v1")
        assert voice.voice_id == "v1"

    def test_get_voice_error(self, client, mocker):
        from typecast.exceptions import NotFoundError
        mock_resp = self._mock_response(mocker, status_code=404, text="not found")
        mocker.patch.object(client.session, "get", return_value=mock_resp)
        with pytest.raises(NotFoundError):
            client.get_voice("missing")

    def test_voices_v2_no_filter(self, client, mocker):
        mock_resp = self._mock_response(mocker, json_data=[])
        get_mock = mocker.patch.object(client.session, "get", return_value=mock_resp)
        client.voices_v2()
        get_mock.assert_called_once_with(f"{client.host}/v2/voices", params={})

    def test_voices_v2_with_filter(self, client, mocker):
        from typecast.models.voices import VoicesV2Filter
        mock_resp = self._mock_response(mocker, json_data=[])
        get_mock = mocker.patch.object(client.session, "get", return_value=mock_resp)
        client.voices_v2(filter=VoicesV2Filter(model="ssfm-v30"))
        called_kwargs = get_mock.call_args.kwargs
        assert called_kwargs["params"].get("model") == "ssfm-v30"

    def test_voices_v2_error(self, client, mocker):
        from typecast.exceptions import InternalServerError
        mock_resp = self._mock_response(mocker, status_code=500, text="boom")
        mocker.patch.object(client.session, "get", return_value=mock_resp)
        with pytest.raises(InternalServerError):
            client.voices_v2()

    def test_voice_v2_success(self, client, mocker):
        mock_resp = self._mock_response(mocker, json_data={"voice_id": "v1", "voice_name": "V1", "models": [{"version": "ssfm-v30", "emotions": ["normal"]}]})
        mocker.patch.object(client.session, "get", return_value=mock_resp)
        voice = client.voice_v2("v1")
        assert voice.voice_id == "v1"

    def test_voice_v2_error(self, client, mocker):
        from typecast.exceptions import NotFoundError
        mock_resp = self._mock_response(mocker, status_code=404, text="not found")
        mocker.patch.object(client.session, "get", return_value=mock_resp)
        with pytest.raises(NotFoundError):
            client.voice_v2("missing")


class TestOutputStreamModel:
    def test_defaults(self):
        out = OutputStream()
        assert out.audio_pitch == 0
        assert out.audio_tempo == 1.0
        assert out.audio_format == "wav"

    def test_no_volume_field(self):
        with pytest.raises(ValidationError):
            OutputStream(volume=100)  # type: ignore[call-arg]

    def test_no_target_lufs_field(self):
        with pytest.raises(ValidationError):
            OutputStream(target_lufs=-14.0)  # type: ignore[call-arg]


class TestTTSRequestStreamModel:
    def test_uses_output_stream(self):
        req = TTSRequestStream(
            voice_id="tc_test",
            text="Hi",
            model="ssfm-v30",
            output=OutputStream(audio_format="mp3"),
        )
        assert req.output is not None
        assert req.output.audio_format == "mp3"

    def test_minimal(self):
        req = TTSRequestStream(voice_id="tc_test", text="Hi", model="ssfm-v21")
        assert req.output is None


class TestSyncStream:
    @pytest.fixture
    def client(self):
        return Typecast(host="https://dummy.example", api_key="test-key")

    @pytest.fixture
    def stream_request(self):
        return TTSRequestStream(voice_id="tc_test", text="Hi", model="ssfm-v30")

    def _mock_stream_response(self, mocker, chunks, status_code=200, text=""):
        m = mocker.Mock()
        m.status_code = status_code
        m.text = text
        # Include an empty chunk to exercise the `if chunk:` filter branch.
        m.iter_content = mocker.Mock(return_value=iter([*chunks, b""]))
        m.close = mocker.Mock()
        return m

    def test_stream_yields_chunks_and_closes(self, client, stream_request, mocker):
        wav_header = b"RIFF\xff\xff\xff\xffWAVEfmt "
        chunks = [wav_header, b"PCM1", b"PCM2"]
        mock_resp = self._mock_stream_response(mocker, chunks)
        post_mock = mocker.patch.object(
            client.session, "post", return_value=mock_resp
        )

        out = list(client.text_to_speech_stream(stream_request, chunk_size=4096))

        assert out == chunks
        post_mock.assert_called_once_with(
            f"{client.host}/v1/text-to-speech/stream",
            json=stream_request.model_dump(exclude_none=True),
            stream=True,
        )
        mock_resp.iter_content.assert_called_once_with(chunk_size=4096)
        mock_resp.close.assert_called_once()

    def test_stream_default_chunk_size(self, client, stream_request, mocker):
        mock_resp = self._mock_stream_response(mocker, [b"a"])
        mocker.patch.object(client.session, "post", return_value=mock_resp)
        list(client.text_to_speech_stream(stream_request))
        mock_resp.iter_content.assert_called_once_with(chunk_size=8192)

    @pytest.mark.parametrize(
        "status,exc_name",
        [
            (400, "BadRequestError"),
            (401, "UnauthorizedError"),
            (402, "PaymentRequiredError"),
            (404, "NotFoundError"),
            (422, "UnprocessableEntityError"),
            (429, "RateLimitError"),
            (500, "InternalServerError"),
            (503, "TypecastError"),
        ],
    )
    def test_stream_error_status_codes(
        self, client, stream_request, mocker, status, exc_name
    ):
        from typecast import exceptions as exc_mod

        exc_class = getattr(exc_mod, exc_name)
        mock_resp = self._mock_stream_response(
            mocker, [], status_code=status, text=f"err {status}"
        )
        mocker.patch.object(client.session, "post", return_value=mock_resp)
        with pytest.raises(exc_class):
            # Materialize the generator so any setup-time HTTP error fires.
            list(client.text_to_speech_stream(stream_request))
        mock_resp.close.assert_called_once()
        # iter_content should never be hit on the error path.
        mock_resp.iter_content.assert_not_called()


class TestSyncSubscription:
    @pytest.fixture
    def client(self):
        return Typecast(host="https://dummy.example", api_key="test-key")

    def _payload(self):
        return {
            "plan": "plus",
            "credits": {"plan_credits": 100000, "used_credits": 1234},
            "limits": {"concurrency_limit": 5},
        }

    def test_get_my_subscription_success(self, client, mocker):
        from typecast.models import PlanTier

        mock_resp = mocker.Mock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = self._payload()
        get_mock = mocker.patch.object(
            client.session, "get", return_value=mock_resp
        )

        sub = client.get_my_subscription()

        get_mock.assert_called_once_with(
            f"{client.host}/v1/users/me/subscription"
        )
        assert sub.plan == PlanTier.PLUS
        assert sub.credits.plan_credits == 100000
        assert sub.credits.used_credits == 1234
        assert sub.limits.concurrency_limit == 5

    @pytest.mark.parametrize(
        "status,exc_name",
        [
            (401, "UnauthorizedError"),
            (429, "RateLimitError"),
            (500, "InternalServerError"),
        ],
    )
    def test_get_my_subscription_errors(self, client, mocker, status, exc_name):
        from typecast import exceptions as exc_mod

        exc_class = getattr(exc_mod, exc_name)
        mock_resp = mocker.Mock()
        mock_resp.status_code = status
        mock_resp.text = f"err {status}"
        mocker.patch.object(client.session, "get", return_value=mock_resp)
        with pytest.raises(exc_class):
            client.get_my_subscription()
