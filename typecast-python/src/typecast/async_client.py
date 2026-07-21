from __future__ import annotations

import asyncio
import sys
from pathlib import Path
from typing import TYPE_CHECKING, Any, AsyncIterator, BinaryIO, Optional, Union
from urllib.parse import quote

if sys.version_info >= (3, 10):  # pragma: no cover - version-specific import
    import aiohttp
else:  # pragma: no cover
    aiohttp = None  # type: ignore[assignment]

from . import conf
from ._voice_clone import (
    normalize_clone_model,
    validate_clone_inputs,
    validate_custom_voice_id,
)
from ._user_agent import aiohttp_user_agent, httpx_user_agent

if TYPE_CHECKING or sys.version_info < (3, 10):  # pragma: no cover
    from ._httpx_compat import AiohttpCompatSession, ClientTimeout, FormData
from .client import _guess_audio_mime
from .client import _output_with_inferred_format
from .client import _validate_output_path
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


class AsyncTypecast:
    """Asynchronous client for the Typecast Text-to-Speech API.

    This client provides async methods to convert text to speech using AI-powered
    voices, with support for multiple models (ssfm-v21, ssfm-v30), emotion control,
    and audio customization.

    Example:
        >>> from typecast import AsyncTypecast
        >>> async with AsyncTypecast(api_key="your-api-key") as client:
        ...     response = await client.text_to_speech(TTSRequest(
        ...         text="Hello world",
        ...         voice_id="tc_62a8975e695ad26f7fb514d1",
        ...         model=TTSModel.SSFM_V21
        ...     ))
        ...     with open("output.wav", "wb") as f:
        ...         f.write(response.audio_data)
    """

    def __init__(
        self,
        host: Optional[str] = None,
        api_key: Optional[str] = None,
        session: Optional[Any] = None,
    ):
        """Initialize the async Typecast client.

        Args:
            host: API host URL. Defaults to TYPECAST_API_HOST env var
                or 'https://api.typecast.ai'.
            api_key: API key for authentication. Defaults to TYPECAST_API_KEY env var.
            session: Optional externally-managed aiohttp.ClientSession. When provided,
                __aenter__ will not create a new session and __aexit__ will not close it
                (the caller owns its lifecycle). Auth headers (`X-API-KEY`, `User-Agent`)
                are attached per-request via `_request_headers()`.

        Raises:
            ValueError: If no API key is provided and TYPECAST_API_KEY is not set
                for the default host.
        """
        self.host = conf.get_host(host)
        self.api_key = conf.get_api_key(api_key)
        if not self.api_key and conf.is_default_host(self.host):
            raise ValueError("API key is required for the default Typecast API host")
        self._owns_session = session is None
        self.session: Optional[Any] = session

    async def __aenter__(self):
        # When an external session is injected, do not create a new one.
        # Per-request auth headers are attached via _request_headers() on each call.
        if self.session is None:
            headers = {
                "User-Agent": (
                    aiohttp_user_agent(self.host)
                    if aiohttp
                    else httpx_user_agent(self.host, "async")
                )
            }
            if self.api_key:
                headers["X-API-KEY"] = self.api_key
            self.session = (
                aiohttp.ClientSession(headers=headers)
                if aiohttp
                else AiohttpCompatSession(headers=headers)
            )
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        # Only close sessions we created. External sessions are caller-owned;
        # leave the reference intact so the client can be re-entered.
        if self.session and self._owns_session:
            await self.session.close()

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
        aiohttp use the session headers. For external sessions, the session has
        no auth headers, so we attach X-API-KEY and User-Agent per-request.
        """
        if self._owns_session:
            return None
        headers = {"User-Agent": aiohttp_user_agent(self.host)}
        if self.api_key:
            headers["X-API-KEY"] = self.api_key
        return headers

    async def text_to_speech(self, request: TTSRequest) -> TTSResponse:
        """Convert text to speech asynchronously.

        Args:
            request: TTS request containing text, voice_id, model, and optional
                settings for emotion, language, and audio output.

        Returns:
            TTSResponse containing audio_data (bytes), duration (float), and format (str).

        Raises:
            TypecastError: If the client session is not initialized.
            BadRequestError: If the request is malformed.
            UnauthorizedError: If the API key is invalid.
            PaymentRequiredError: If credits are insufficient.
            UnprocessableEntityError: If validation fails.
        """
        if not self.session:
            raise TypecastError("Client session not initialized. Use async with.")
        endpoint = "/v1/text-to-speech"
        async with self.session.post(
            f"{self.host}{endpoint}",
            json=request.model_dump(exclude_none=True),
            headers=self._request_headers(),
        ) as response:
            if response.status != 200:
                error_text = await response.text()
                self._handle_error(response.status, error_text)

            audio_data = await response.read()
            return TTSResponse(
                audio_data=audio_data,
                duration=float(response.headers.get("X-Audio-Duration", 0)),
                format=response.headers.get("Content-Type", "audio/wav").split("/")[-1],
            )

    async def generate_to_file(
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

        Browse available API voices at
        ``https://typecast.ai/developers/api/voices``.
        """
        output_path = _validate_output_path(path)
        response = await self.text_to_speech(
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
        loop = asyncio.get_running_loop()
        await loop.run_in_executor(None, output_path.write_bytes, response.audio_data)
        return response

    async def text_to_speech_stream(
        self, request: TTSRequestStream, chunk_size: int = 8192
    ) -> AsyncIterator[bytes]:
        """Stream synthesized audio from `POST /v1/text-to-speech/stream`.

        Async generator that yields audio chunks as the server emits them.
        For WAV the first chunk contains the WAV header (declared with size
        0xFFFFFFFF for streaming) followed by PCM data; subsequent chunks are
        PCM only. For MP3 each chunk contains independently-decodable frames.

        Args:
            request: Streaming TTS request. Uses `OutputStream`, which omits
                `volume` (not supported by the streaming endpoint).
            chunk_size: Maximum bytes returned per yielded chunk.

        Yields:
            Audio chunk bytes in the order produced by the server.

        Raises:
            TypecastError: If the client session is not initialized.
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
        if not self.session:
            raise TypecastError("Client session not initialized. Use async with.")
        endpoint = "/v1/text-to-speech/stream"
        stream_timeout = (
            aiohttp.ClientTimeout(sock_connect=10, sock_read=300)
            if aiohttp
            else ClientTimeout(sock_connect=10, sock_read=300)
        )
        async with self.session.post(
            f"{self.host}{endpoint}",
            json=request.model_dump(exclude_none=True),
            timeout=stream_timeout,
            headers=self._request_headers(),
        ) as response:
            if response.status != 200:
                error_text = await response.text()
                self._handle_error(response.status, error_text)

            async for chunk in response.content.iter_chunked(
                chunk_size
            ):  # pragma: no branch
                yield chunk

    async def text_to_speech_with_timestamps(
        self,
        request: TTSRequestWithTimestamps,
        granularity: Optional[str] = None,
    ) -> TTSWithTimestampsResponse:
        """Async version of ``Typecast.text_to_speech_with_timestamps``.

        Args:
            request: Request body (same shape as ``TTSRequest``).
            granularity: Optional ``"word"`` or ``"char"`` filter.

        Returns:
            ``TTSWithTimestampsResponse`` with helpers ``to_srt()``,
            ``to_vtt()``, ``save_audio()``.

        Raises:
            TypecastError: If the client session is not initialized
                (i.e. used outside ``async with``).
            ValueError: If ``granularity`` is not ``None``, ``"word"``, or ``"char"``.
            BadRequestError, UnauthorizedError, PaymentRequiredError,
            NotFoundError, UnprocessableEntityError, RateLimitError,
            InternalServerError, TypecastError: per HTTP status.
        """
        if self.session is None:
            raise TypecastError("Client session not initialized; use 'async with'.")
        if granularity not in (None, "word", "char"):
            raise ValueError(
                f"granularity must be None, 'word', or 'char'; got {granularity!r}"
            )
        endpoint = "/v1/text-to-speech/with-timestamps"
        params = {"granularity": granularity} if granularity else None
        async with self.session.post(
            f"{self.host}{endpoint}",
            json=request.model_dump(exclude_none=True),
            params=params,
            headers=self._request_headers(),
        ) as response:
            if response.status != 200:
                text = await response.text()
                self._handle_error(response.status, text)
            data = await response.json()
        return TTSWithTimestampsResponse.model_validate(data)

    async def clone_voice(
        self,
        audio: Union[str, Path, bytes, BinaryIO],
        name: str,
        model: Union[str, "TTSModel"],
    ) -> CustomVoice:
        """Create a quick-cloned custom voice from an audio sample (async).

        Args:
            audio: Audio sample. Accepts file path (str/Path), raw bytes,
                or a readable binary file object. Max 25 MB.
            name: Voice name, 1-30 characters.
            model: Engine model. ``"ssfm-v21"`` or ``"ssfm-v30"`` (or ``TTSModel`` enum).

        Returns:
            ``CustomVoice`` with ``voice_id`` (uc_ prefix), ``name``, and ``model``.

        Raises:
            ValueError: name length out of range or audio exceeds 25 MB.
            FileNotFoundError: ``audio`` is a path to a non-existent file.
            TypecastError: client session not initialized or HTTP error.
        """
        if self.session is None:
            raise TypecastError("Client session not initialized; use 'async with'.")

        audio_bytes, filename = validate_clone_inputs(audio, name)
        model_str = normalize_clone_model(model)

        form: Any = aiohttp.FormData() if aiohttp else FormData()
        form.add_field("name", name)
        form.add_field("model", model_str)
        form.add_field(
            "file",
            audio_bytes,
            filename=filename,
            content_type=_guess_audio_mime(filename),
        )
        timeout = (
            aiohttp.ClientTimeout(total=300, connect=10)
            if aiohttp
            else ClientTimeout(total=300, connect=10)
        )
        async with self.session.post(
            f"{self.host}/v1/voices/clone",
            data=form,
            timeout=timeout,
            headers=self._request_headers(),
        ) as response:
            if response.status != 200:
                text = await response.text()
                self._handle_error(response.status, text)
            body = await response.json()
            return CustomVoice.model_validate(body)

    async def delete_voice(self, voice_id: str) -> None:
        """Soft-delete a custom voice (async).

        Args:
            voice_id: Voice identifier with ``uc_`` prefix.

        Raises:
            TypecastError subclasses: per HTTP status from the API.
        """
        if self.session is None:
            raise TypecastError("Client session not initialized; use 'async with'.")

        validate_custom_voice_id(voice_id)
        timeout = (
            aiohttp.ClientTimeout(total=60, connect=10)
            if aiohttp
            else ClientTimeout(total=60, connect=10)
        )
        async with self.session.delete(
            f"{self.host}/v1/voices/{quote(voice_id, safe='')}",
            timeout=timeout,
            headers=self._request_headers(),
        ) as response:
            if response.status not in (200, 204):
                text = await response.text()
                self._handle_error(response.status, text)

    async def voices(self, model: Optional[str] = None) -> list[VoicesResponse]:
        """Get available voices (V1 API) asynchronously.

        Args:
            model: Optional model filter (e.g., 'ssfm-v21', 'ssfm-v30').

        Returns:
            List of VoicesResponse objects with voice information.

        Note:
            This method is deprecated. Use voices_v2() for enhanced metadata
            and filtering options.
        """
        if not self.session:
            raise TypecastError("Client session not initialized. Use async with.")
        endpoint = "/v1/voices"
        params = {}
        if model:
            params["model"] = model

        async with self.session.get(
            f"{self.host}{endpoint}",
            params=params,
            headers=self._request_headers(),
        ) as response:
            if response.status != 200:
                error_text = await response.text()
                self._handle_error(response.status, error_text)

            data = await response.json()
            return [VoicesResponse.model_validate(item) for item in data]

    async def get_voice(self, voice_id: str) -> VoicesResponse:
        """Get a specific voice by ID (V1 API) asynchronously.

        Args:
            voice_id: The voice ID (e.g., 'tc_62a8975e695ad26f7fb514d1').

        Returns:
            VoicesResponse with voice information and available emotions.

        Raises:
            NotFoundError: If the voice ID does not exist.

        Note:
            This method is deprecated. Use voices_v2() for enhanced metadata.
        """
        if not self.session:
            raise TypecastError("Client session not initialized. Use async with.")
        endpoint = f"/v1/voices/{voice_id}"

        async with self.session.get(
            f"{self.host}{endpoint}", headers=self._request_headers()
        ) as response:
            if response.status != 200:
                error_text = await response.text()
                self._handle_error(response.status, error_text)

            data = await response.json()
            # API returns a list, so we take the first element
            if isinstance(data, list) and len(data) > 0:
                return VoicesResponse.model_validate(data[0])
            return VoicesResponse.model_validate(data)

    async def voices_v2(
        self, filter: Optional[VoicesV2Filter] = None
    ) -> list[VoiceV2Response]:
        """Get voices with enhanced metadata (V2 API)

        Returns voices with model-grouped emotions and additional metadata.

        Args:
            filter: Optional filter options (model, gender, age, use_cases)

        Returns:
            List of VoiceV2Response objects
        """
        if not self.session:
            raise TypecastError("Client session not initialized. Use async with.")
        endpoint = "/v2/voices"
        params = {}
        if filter:
            filter_dict = filter.model_dump(exclude_none=True)
            # Convert enum values to their underlying str representation.
            # Every VoicesV2Filter field is an Optional[Enum], so getattr
            # falls back only if a future non-enum field is added.
            for key, value in filter_dict.items():
                params[key] = getattr(value, "value", value)

        async with self.session.get(
            f"{self.host}{endpoint}",
            params=params,
            headers=self._request_headers(),
        ) as response:
            if response.status != 200:
                error_text = await response.text()
                self._handle_error(response.status, error_text)

            data = await response.json()
            return [VoiceV2Response.model_validate(item) for item in data]

    async def get_my_subscription(self) -> SubscriptionResponse:
        """Get the authenticated user's current subscription asynchronously.

        Returns plan tier, credit usage, and concurrency limits. Use this to
        check remaining credits or verify your plan before making TTS calls.

        Returns:
            SubscriptionResponse with plan, credits, and limits.

        Raises:
            TypecastError: If the client session is not initialized.
            UnauthorizedError: If the API key is invalid.
            RateLimitError: If the rate limit was exceeded.
            InternalServerError: On server-side failures.
        """
        if not self.session:
            raise TypecastError("Client session not initialized. Use async with.")
        endpoint = "/v1/users/me/subscription"
        async with self.session.get(
            f"{self.host}{endpoint}", headers=self._request_headers()
        ) as response:
            if response.status != 200:
                error_text = await response.text()
                self._handle_error(response.status, error_text)
            data = await response.json()
            return SubscriptionResponse.model_validate(data)

    async def voice_v2(self, voice_id: str) -> VoiceV2Response:
        """Get a specific voice by ID with enhanced metadata (V2 API)

        Args:
            voice_id: The voice ID (e.g., 'tc_62a8975e695ad26f7fb514d1')

        Returns:
            VoiceV2Response with voice information and metadata

        Raises:
            NotFoundError: If the voice ID does not exist.
        """
        if not self.session:
            raise TypecastError("Client session not initialized. Use async with.")
        endpoint = f"/v2/voices/{voice_id}"

        async with self.session.get(
            f"{self.host}{endpoint}", headers=self._request_headers()
        ) as response:
            if response.status != 200:
                error_text = await response.text()
                self._handle_error(response.status, error_text)

            data = await response.json()
            return VoiceV2Response.model_validate(data)

    async def recommend_voices(
        self, query: str, count: int = 5
    ) -> list[RecommendedVoice]:
        """Recommend voices from a text description.

        Recommendation results only include ``voice_id``, ``voice_name``, and
        ``score``. Use ``voice_v2`` or ``voices_v2`` to fetch detailed metadata
        for returned voice IDs.
        """
        if count < 1 or count > 10:
            raise ValueError("count must be between 1 and 10")
        if not self.session:
            raise TypecastError("Client session not initialized. Use async with.")

        async with self.session.get(
            f"{self.host}/v1/voices/recommendations",
            params={"query": query, "count": count},
        ) as response:
            if response.status != 200:
                error_text = await response.text()
                self._handle_error(response.status, error_text)

            data = await response.json()
            return [RecommendedVoice.model_validate(item) for item in data]
