from enum import Enum
from typing import Optional

from pydantic import BaseModel, Field

from .tts import TTSModel


class VoicesResponse(BaseModel):
    """V1 Voices response (deprecated, use VoiceV2Response instead)"""

    voice_id: str
    voice_name: str
    model: str
    emotions: list[str]


class GenderEnum(str, Enum):
    """Gender classification for voices"""

    MALE = "male"
    FEMALE = "female"


class AgeEnum(str, Enum):
    """Age group classification for voices"""

    CHILD = "child"
    TEENAGER = "teenager"
    YOUNG_ADULT = "young_adult"
    MIDDLE_AGE = "middle_age"
    ELDER = "elder"


class UseCaseEnum(str, Enum):
    """Use case categories for voices"""

    ANNOUNCER = "Announcer"
    ANIME = "Anime"
    AUDIOBOOK = "Audiobook"
    CONVERSATIONAL = "Conversational"
    DOCUMENTARY = "Documentary"
    E_LEARNING = "E-learning"
    RAPPER = "Rapper"
    GAME = "Game"
    TIKTOK_REELS = "Tiktok/Reels"
    NEWS = "News"
    PODCAST = "Podcast"
    VOICEMAIL = "Voicemail"
    ADS = "Ads"


class ModelInfo(BaseModel):
    """Model information with supported emotions"""

    version: TTSModel
    emotions: list[str]


class VoiceV2Response(BaseModel):
    """V2 Voice response with enhanced metadata"""

    voice_id: str
    voice_name: str
    models: list[ModelInfo]
    gender: Optional[GenderEnum] = None
    age: Optional[AgeEnum] = None
    use_cases: Optional[list[str]] = None


class VoicesV2Filter(BaseModel):
    """Filter options for V2 voices endpoint"""

    model: Optional[TTSModel] = None
    gender: Optional[GenderEnum] = None
    age: Optional[AgeEnum] = None
    use_cases: Optional[UseCaseEnum] = None


class CustomVoice(BaseModel):
    """Quick-cloned custom voice returned by `POST /v1/voices/clone`.

    Attributes:
        voice_id: Custom voice identifier with `uc_` prefix.
            Use this value as `voice_id` in `text_to_speech` / `text_to_speech_with_timestamps`.
        name: Human-readable name (1-30 chars).
        model: Engine model the voice was cloned for (`ssfm-v21` or `ssfm-v30`).
    """

    voice_id: str = Field(..., description="Custom voice identifier (uc_ prefix)")
    name: str = Field(..., description="Human-readable voice name")
    model: str = Field(..., description="Engine model: ssfm-v21 or ssfm-v30")
