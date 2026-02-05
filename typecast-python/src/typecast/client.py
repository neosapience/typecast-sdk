from typing import Optional

import requests

from . import conf
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
from .models import TTSRequest, TTSResponse, VoicesResponse, VoiceV2Response, VoicesV2Filter


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

    def __init__(self, host: Optional[str] = None, api_key: Optional[str] = None):
        """Initialize the Typecast client.

        Args:
            host: API host URL. Defaults to TYPECAST_API_HOST env var
                or 'https://api.typecast.ai'.
            api_key: API key for authentication. Defaults to TYPECAST_API_KEY env var.

        Raises:
            ValueError: If no API key is provided and TYPECAST_API_KEY is not set.
        """
        self.host = conf.get_host(host)
        self.api_key = conf.get_api_key(api_key)
        self.session = requests.Session()
        self.session.headers.update(
            {"X-API-KEY": self.api_key, "Content-Type": "application/json"}
        )

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
            f"{self.host}{endpoint}", json=request.model_dump(exclude_none=True)
        )
        if response.status_code != 200:
            self._handle_error(response.status_code, response.text)

        return TTSResponse(
            audio_data=response.content,
            duration=response.headers.get("X-Audio-Duration", 0),
            format=response.headers.get("Content-Type", "audio/wav").split("/")[-1],
        )

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

        response = self.session.get(f"{self.host}{endpoint}", params=params)

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
        response = self.session.get(f"{self.host}{endpoint}")

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
            # Convert enum values to strings
            for key, value in filter_dict.items():
                if hasattr(value, "value"):
                    params[key] = value.value
                else:
                    params[key] = value

        response = self.session.get(f"{self.host}{endpoint}", params=params)

        if response.status_code != 200:
            self._handle_error(response.status_code, response.text)

        return [VoiceV2Response.model_validate(item) for item in response.json()]

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
        response = self.session.get(f"{self.host}{endpoint}")

        if response.status_code != 200:
            self._handle_error(response.status_code, response.text)

        return VoiceV2Response.model_validate(response.json())
