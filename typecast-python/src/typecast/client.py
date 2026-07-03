from pathlib import Path
from typing import BinaryIO, Iterator, Optional, Union
from urllib.parse import quote

import requests

from . import conf
from ._voice_clone import (
    normalize_clone_model,
    validate_clone_inputs,
    validate_custom_voice_id,
)
from ._user_agent import requests_user_agent
from .composer import SpeechComposer
from .exceptions import (
    BadRequestError,
    InternalServerError,
    NotFoundError,
    PaymentRequiredError,
    RateLimitError,
    TypecastError,
    UnauthorizedError,
    UnprocessableEntityError,
)
from .models import (
    CustomVoice,
    LanguageCode,
    Output,
    RecommendedVoice,
    SubscriptionResponse,
    TTSModel,
    TTSPrompt,
    TTSRequest,
    TTSRequestStream,
    TTSRequestWithTimestamps,
    TTSResponse,
    TTSWithTimestampsResponse,
    VoicesResponse,
    VoicesV2Filter,
    VoiceV2Response,
)


def _infer_audio_format_from_path(path: Union[str, Path]) -> Optional[str]:
    suffix = Path(path).suffix.lower()
    if suffix == ".mp3":
        return "mp3"
    if suffix == ".wav":
        return "wav"
    return None


def _output_with_inferred_format(
    output: Optional[Output],
    path: Union[str, Path],
) -> Optional[Output]:
    audio_format = _infer_audio_format_from_path(path)
    if audio_format is None or (output is not None and output.audio_format is not None):
        return output
    if output is None:
        return Output(audio_format=audio_format)
    return output.model_copy(update={"audio_format": audio_format})


def _validate_output_path(path: Union[str, Path]) -> Path:
    if isinstance(path, str) and not path.strip():
        raise ValueError("path cannot be empty")
    return Path(path)


def _guess_audio_mime(filename: str) -> str:
    """Guess audio MIME type from filename extension; fall back to octet-stream."""
    lower = filename.lower()
    if lower.endswith(".wav"):
        return "audio/wav"
    if lower.endswith(".mp3"):
        return "audio/mpeg"
    return "application/octet-stream"


class Typecast:
    """Synchronous client for the Typecast Text-to-Speech API.

    This client provides methods to convert text to speech using AI-powered voices,
    with support for multiple models (ssfm-v21, ssfm-v30), emotion control, and
    audio customization.

    Example:
        >>> from typecast import Typecast
        >>> client = Typecast(api_key="your-api-key")
        >>> response = client.text_to_speech(TTSRequest(
        ...     text="Hello world",
        ...     voice_id="tc_62a8975e695ad26f7fb514d1",
        ...     model=TTSModel.SSFM_V21
        ... ))
        >>> with open("output.wav", "wb") as f:
        ...     f.write(response.audio_data)
    """

    def __init__(
        self,
        host: Optional[str] = None,
        api_key: Optional[str] = None,
        session: Optional[requests.Session] = None,
    ):
        """Initialize the Typecast client.

        Args:
            host: API host URL. Defaults to TYPECAST_API_HOST env var
                or 'https://api.typecast.ai'.
            api_key: API key for authentication. Defaults to TYPECAST_API_KEY env var.
            session: Optional externally-managed requests.Session. When provided,
                the client will not create a new session nor close it; auth headers
                (`X-API-KEY`, `User-Agent`) are attached per-request via
                `_request_headers()`.

        Raises:
            ValueError: If no API key is provided and TYPECAST_API_KEY is not set.
        """
        self.host = conf.get_host(host)
        self.api_key = conf.get_api_key(api_key)
        if not self.api_key and conf.is_default_host(self.host):
            raise ValueError("API key is required for the default Typecast API host")
        self._owns_session = session is None
        if session is not None:
            self.session = session
        else:
            self.session = requests.Session()
            headers = {
                "Content-Type": "application/json",
                "User-Agent": requests_user_agent(self.host),
            }
            if self.api_key:
                headers["X-API-KEY"] = self.api_key
            self.session.headers.update(headers)

    def _handle_error(self, status_code: int, response_text: str):
        """Handle HTTP error responses with specific exception types."""
        if status_code == 400:
            raise BadRequestError(f"Bad request: {response_text}")
        elif status_code == 401:
            raise UnauthorizedError(f"Unauthorized: {response_text}")
        elif status_code == 402:
            raise PaymentRequiredError(f"Payment required: {response_text}")
        elif status_code == 404:
            raise NotFoundError(f"Not found: {response_text}")
        elif status_code == 422:
            raise UnprocessableEntityError(f"Validation error: {response_text}")
        elif status_code == 429:
            raise RateLimitError(f"Rate limit exceeded: {response_text}")
        elif status_code == 500:
            raise InternalServerError(f"Internal server error: {response_text}")
        else:
            raise TypecastError(
                f"API request failed: {status_code}, {response_text}",
                status_code=status_code,
            )

    def _request_headers(self) -> Optional[dict]:
        """Headers to attach to each individual request.

        For owned sessions, auth is set at session scope, so return None and let
        requests use the session headers. For external sessions, the session has
        no auth headers, so we attach X-API-KEY and User-Agent per-request.
        """
        if self._owns_session:
            return None
        headers = {"User-Agent": requests_user_agent(self.host)}
        if self.api_key:
            headers["X-API-KEY"] = self.api_key
        return headers

    def text_to_speech(self, request: TTSRequest) -> TTSResponse:
        """Convert text to speech.

        Args:
            request: TTS request containing text, voice_id, model, and optional
                settings for emotion, language, and audio output.

        Returns:
            TTSResponse containing audio_data (bytes), duration (float), and format (str).

        Raises:
            BadRequestError: If the request is malformed.
            UnauthorizedError: If the API key is invalid.
            PaymentRequiredError: If credits are insufficient.
            UnprocessableEntityError: If validation fails.
        """
        endpoint = "/v1/text-to-speech"
        response = self.session.post(
            f"{self.host}{endpoint}",
            json=request.model_dump(exclude_none=True),
            headers=self._request_headers(),
        )
        if response.status_code != 200:
            self._handle_error(response.status_code, response.text)

        return TTSResponse(
            audio_data=response.content,
            duration=response.headers.get("X-Audio-Duration", 0),
            format=response.headers.get("Content-Type", "audio/wav").split("/")[-1],
        )

    def compose_speech(self) -> SpeechComposer:
        """Build composed speech from multiple text and pause segments.

        Text passed to ``say()`` may include pause markup such as ``<|0.3s|>``.
        ``pause(seconds)`` also uses seconds, e.g. ``0.3`` for 300 ms.
        """
        return SpeechComposer(self.text_to_speech)

    def generate_to_file(
        self,
        path: Union[str, Path],
        *,
        text: str,
        voice_id: str,
        model: TTSModel = TTSModel.SSFM_V30,
        language: Optional[Union[LanguageCode, str]] = None,
        prompt: Optional[TTSPrompt] = None,
        output: Optional[Output] = None,
        seed: Optional[int] = None,
    ) -> TTSResponse:
        """Convert text to speech and write the audio bytes to a file.

        ``model`` defaults to ``ssfm-v30``. If ``output.audio_format`` is not
        set, the format is inferred from a ``.mp3`` or ``.wav`` extension.
        Browse available API voices at
        ``https://typecast.ai/developers/api/voices``.
        """
        output_path = _validate_output_path(path)
        response = self.text_to_speech(
            TTSRequest(
                text=text,
                voice_id=voice_id,
                model=model,
                language=language,
                prompt=prompt,
                output=_output_with_inferred_format(output, path),
                seed=seed,
            )
        )
        output_path.write_bytes(response.audio_data)
        return response

    def text_to_speech_stream(
        self, request: TTSRequestStream, chunk_size: int = 8192
    ) -> Iterator[bytes]:
        """Stream synthesized audio from `POST /v1/text-to-speech/stream`.

        Yields raw audio chunks as the server produces them. For WAV the
        first chunk contains the WAV header (declared with size 0xFFFFFFFF
        for streaming) followed by PCM data; subsequent chunks are PCM only.
        For MP3 each chunk contains independently-decodable MP3 frames.

        The HTTP response is held open until the iterator is exhausted or
        garbage-collected, so callers should consume the iterator promptly
        (e.g. inside a `for` loop or by writing chunks to disk).

        Args:
            request: Streaming TTS request. Uses `OutputStream`, which omits
                `volume` (not supported by the streaming endpoint).
            chunk_size: Maximum bytes returned per yielded chunk.

        Yields:
            Audio chunk bytes in the order produced by the server.

        Raises:
            BadRequestError, UnauthorizedError, PaymentRequiredError,
            NotFoundError, UnprocessableEntityError, RateLimitError,
            InternalServerError, TypecastError: depending on response status.
        """
        if (
            not isinstance(chunk_size, int)
            or isinstance(chunk_size, bool)
            or chunk_size < 1
        ):
            raise ValueError("chunk_size must be a positive integer")
        endpoint = "/v1/text-to-speech/stream"
        response = self.session.post(
            f"{self.host}{endpoint}",
            json=request.model_dump(exclude_none=True),
            stream=True,
            timeout=(10, 300),
            headers=self._request_headers(),
        )
        if response.status_code != 200:
            error_text = response.text
            response.close()
            self._handle_error(response.status_code, error_text)

        try:
            for chunk in response.iter_content(chunk_size=chunk_size):
                if chunk:
                    yield chunk
        finally:
            response.close()

    def text_to_speech_with_timestamps(
        self,
        request: TTSRequestWithTimestamps,
        granularity: Optional[str] = None,
    ) -> TTSWithTimestampsResponse:
        """Synthesize speech and return base64 audio + alignment timestamps.

        Args:
            request: Request body (same shape as `TTSRequest`).
            granularity: Optional ``"word"`` or ``"char"`` to filter the
                returned alignment arrays. Omit to receive both.

        Returns:
            ``TTSWithTimestampsResponse`` with ``audio`` (base64),
            ``words``, ``characters``, and helper methods ``to_srt()``,
            ``to_vtt()``, and ``save_audio()``.

        Raises:
            ValueError: If ``granularity`` is not ``None``, ``"word"``, or ``"char"``.
            BadRequestError, UnauthorizedError, PaymentRequiredError,
            NotFoundError, UnprocessableEntityError, RateLimitError,
            InternalServerError, TypecastError: per HTTP status.
        """
        if granularity not in (None, "word", "char"):
            raise ValueError(
                f"granularity must be None, 'word', or 'char'; got {granularity!r}"
            )
        endpoint = "/v1/text-to-speech/with-timestamps"
        params = {"granularity": granularity} if granularity else None
        response = self.session.post(
            f"{self.host}{endpoint}",
            json=request.model_dump(exclude_none=True),
            params=params,
            timeout=(10, 300),
            headers=self._request_headers(),
        )
        if response.status_code != 200:
            self._handle_error(response.status_code, response.text)
        return TTSWithTimestampsResponse.model_validate(response.json())

    def clone_voice(
        self,
        audio: Union[str, Path, bytes, BinaryIO],
        name: str,
        model: Union[str, "TTSModel"],
    ) -> CustomVoice:
        """Create a quick-cloned custom voice from an audio sample.

        Args:
            audio: Audio sample. Accepts file path (str/Path), raw bytes,
                or a readable binary file object. Max 25 MB.
            name: Voice name, 1-30 characters.
            model: Engine model. ``"ssfm-v21"`` or ``"ssfm-v30"`` (or ``TTSModel`` enum).

        Returns:
            ``CustomVoice`` with ``voice_id`` (uc_ prefix), ``name``, and ``model``.
            Use ``voice_id`` directly with ``text_to_speech`` to synthesize.

        Raises:
            ValueError: name length out of range or audio exceeds 25 MB.
            FileNotFoundError: ``audio`` is a path to a non-existent file.
            TypecastError subclasses: per HTTP status from the API.
        """
        audio_bytes, filename = validate_clone_inputs(audio, name)
        model_str = normalize_clone_model(model)

        files = {
            "file": (filename, audio_bytes, _guess_audio_mime(filename)),
        }
        data = {"name": name, "model": model_str}
        # Remove the session-level Content-Type so requests can set the
        # correct multipart/form-data boundary for this request. For external
        # sessions, also attach the per-request auth headers (X-API-KEY,
        # User-Agent); _request_headers() returns None for owned sessions, so
        # owned-session behavior is unchanged.
        headers = {"Content-Type": None}
        per_request = self._request_headers()
        if per_request:
            headers.update(per_request)
        response = self.session.post(
            f"{self.host}/v1/voices/clone",
            files=files,
            data=data,
            headers=headers,
            timeout=(10, 300),
        )
        if response.status_code != 200:
            self._handle_error(response.status_code, response.text)
        return CustomVoice.model_validate(response.json())

    def delete_voice(self, voice_id: str) -> None:
        """Soft-delete a custom voice.

        Args:
            voice_id: Voice identifier with ``uc_`` prefix (returned by ``clone_voice``).

        Raises:
            TypecastError subclasses: per HTTP status from the API
                (e.g., ``NotFoundError`` if the voice doesn't exist or isn't owned).
        """
        validate_custom_voice_id(voice_id)
        response = self.session.delete(
            f"{self.host}/v1/voices/{quote(voice_id, safe='')}",
            timeout=(10, 60),
            headers=self._request_headers(),
        )
        if response.status_code not in (200, 204):
            self._handle_error(response.status_code, response.text)

    def voices(self, model: Optional[str] = None) -> list[VoicesResponse]:
        """Get available voices (V1 API).

        Args:
            model: Optional model filter (e.g., 'ssfm-v21', 'ssfm-v30').

        Returns:
            List of VoicesResponse objects with voice information.

        Note:
            This method is deprecated. Use voices_v2() for enhanced metadata
            and filtering options.
        """
        endpoint = "/v1/voices"
        params = {}
        if model:
            params["model"] = model

        response = self.session.get(
            f"{self.host}{endpoint}", params=params, headers=self._request_headers()
        )

        if response.status_code != 200:
            self._handle_error(response.status_code, response.text)

        return [VoicesResponse.model_validate(item) for item in response.json()]

    def get_voice(self, voice_id: str) -> VoicesResponse:
        """Get a specific voice by ID (V1 API).

        Args:
            voice_id: The voice ID (e.g., 'tc_62a8975e695ad26f7fb514d1').

        Returns:
            VoicesResponse with voice information and available emotions.

        Raises:
            NotFoundError: If the voice ID does not exist.

        Note:
            This method is deprecated. Use voices_v2() for enhanced metadata.
        """
        endpoint = f"/v1/voices/{voice_id}"
        response = self.session.get(
            f"{self.host}{endpoint}", headers=self._request_headers()
        )

        if response.status_code != 200:
            self._handle_error(response.status_code, response.text)

        data = response.json()
        # API returns a list, so we take the first element
        if isinstance(data, list) and len(data) > 0:
            return VoicesResponse.model_validate(data[0])
        return VoicesResponse.model_validate(data)

    def voices_v2(
        self, filter: Optional[VoicesV2Filter] = None
    ) -> list[VoiceV2Response]:
        """Get voices with enhanced metadata (V2 API)

        Returns voices with model-grouped emotions and additional metadata.

        Args:
            filter: Optional filter options (model, gender, age, use_cases)

        Returns:
            List of VoiceV2Response objects
        """
        endpoint = "/v2/voices"
        params = {}
        if filter:
            filter_dict = filter.model_dump(exclude_none=True)
            # Convert enum values to their underlying str representation.
            # Every VoicesV2Filter field is an Optional[Enum], so getattr
            # falls back only if a future non-enum field is added.
            for key, value in filter_dict.items():
                params[key] = getattr(value, "value", value)

        response = self.session.get(
            f"{self.host}{endpoint}", params=params, headers=self._request_headers()
        )

        if response.status_code != 200:
            self._handle_error(response.status_code, response.text)

        return [VoiceV2Response.model_validate(item) for item in response.json()]

    def get_my_subscription(self) -> SubscriptionResponse:
        """Get the authenticated user's current subscription.

        Returns plan tier, credit usage, and concurrency limits. Use this to
        check remaining credits or verify your plan before making TTS calls.

        Returns:
            SubscriptionResponse with plan, credits, and limits.

        Raises:
            UnauthorizedError: If the API key is invalid.
            RateLimitError: If the rate limit was exceeded.
            InternalServerError: On server-side failures.
        """
        endpoint = "/v1/users/me/subscription"
        response = self.session.get(
            f"{self.host}{endpoint}", headers=self._request_headers()
        )
        if response.status_code != 200:
            self._handle_error(response.status_code, response.text)
        return SubscriptionResponse.model_validate(response.json())

    def voice_v2(self, voice_id: str) -> VoiceV2Response:
        """Get a specific voice by ID with enhanced metadata (V2 API)

        Args:
            voice_id: The voice ID (e.g., 'tc_62a8975e695ad26f7fb514d1')

        Returns:
            VoiceV2Response with voice information and metadata

        Raises:
            NotFoundError: If the voice ID does not exist.
        """
        endpoint = f"/v2/voices/{voice_id}"
        response = self.session.get(
            f"{self.host}{endpoint}", headers=self._request_headers()
        )

        if response.status_code != 200:
            self._handle_error(response.status_code, response.text)

        return VoiceV2Response.model_validate(response.json())

    def recommend_voices(self, query: str, count: int = 5) -> list[RecommendedVoice]:
        """Recommend voices from a text description.

        Recommendation results only include ``voice_id``, ``voice_name``, and
        ``score``. Use ``voice_v2`` or ``voices_v2`` to fetch detailed metadata
        for returned voice IDs.

        Args:
            query: Text description of the desired voice.
            count: Number of recommendations to return, from 1 to 10. Defaults to 5.
        """
        if count < 1 or count > 10:
            raise ValueError("count must be between 1 and 10")

        response = self.session.get(
            f"{self.host}/v1/voices/recommendations",
            params={"query": query, "count": count},
        )
        if response.status_code != 200:
            self._handle_error(response.status_code, response.text)
        return [RecommendedVoice.model_validate(item) for item in response.json()]
