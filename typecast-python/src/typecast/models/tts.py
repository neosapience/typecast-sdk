from enum import Enum
from typing import Literal, Optional, Union

from pydantic import BaseModel, ConfigDict, Field


class TTSModel(str, Enum):
    SSFM_V21 = "ssfm-v21"
    SSFM_V30 = "ssfm-v30"


class LanguageCode(str, Enum):
    """ISO 639-3 language codes supported by Typecast API

    ssfm-v21: 27 languages
    ssfm-v30: 37 languages (includes all v21 languages plus additional ones)
    """

    ENG = "eng"  # English
    KOR = "kor"  # Korean
    SPA = "spa"  # Spanish
    DEU = "deu"  # German
    FRA = "fra"  # French
    ITA = "ita"  # Italian
    POL = "pol"  # Polish
    NLD = "nld"  # Dutch
    RUS = "rus"  # Russian
    JPN = "jpn"  # Japanese
    ELL = "ell"  # Greek
    TAM = "tam"  # Tamil
    TGL = "tgl"  # Tagalog
    FIN = "fin"  # Finnish
    ZHO = "zho"  # Chinese
    SLK = "slk"  # Slovak
    ARA = "ara"  # Arabic
    HRV = "hrv"  # Croatian
    UKR = "ukr"  # Ukrainian
    IND = "ind"  # Indonesian
    DAN = "dan"  # Danish
    SWE = "swe"  # Swedish
    MSA = "msa"  # Malay
    CES = "ces"  # Czech
    POR = "por"  # Portuguese
    BUL = "bul"  # Bulgarian
    RON = "ron"  # Romanian
    # ssfm-v30 additional languages
    BEN = "ben"  # Bengali
    HIN = "hin"  # Hindi
    HUN = "hun"  # Hungarian
    NAN = "nan"  # Min Nan
    NOR = "nor"  # Norwegian
    PAN = "pan"  # Punjabi
    THA = "tha"  # Thai
    TUR = "tur"  # Turkish
    VIE = "vie"  # Vietnamese
    YUE = "yue"  # Cantonese


class EmotionPreset(str, Enum):
    """Emotion preset types

    ssfm-v21: normal, happy, sad, angry
    ssfm-v30: normal, happy, sad, angry, whisper, toneup, tonedown
    """

    NORMAL = "normal"
    HAPPY = "happy"
    SAD = "sad"
    ANGRY = "angry"
    WHISPER = "whisper"  # ssfm-v30 only
    TONEUP = "toneup"  # ssfm-v30 only
    TONEDOWN = "tonedown"  # ssfm-v30 only


class Prompt(BaseModel):
    """Emotion and style settings for ssfm-v21 model"""

    emotion_preset: Optional[str] = Field(
        default="normal",
        description="Emotion preset",
        examples=["normal", "happy", "sad", "angry"],
    )
    emotion_intensity: Optional[float] = Field(default=1.0, ge=0.0, le=2.0)


class PresetPrompt(BaseModel):
    """Preset-based emotion control for ssfm-v30 model"""

    emotion_type: Literal["preset"] = Field(
        default="preset",
        description="Must be 'preset' for preset-based emotion control",
    )
    emotion_preset: Optional[str] = Field(
        default="normal",
        description="Emotion preset to apply",
        examples=["normal", "happy", "sad", "angry", "whisper", "toneup", "tonedown"],
    )
    emotion_intensity: Optional[float] = Field(default=1.0, ge=0.0, le=2.0)


class SmartPrompt(BaseModel):
    """Context-aware emotion inference for ssfm-v30 model"""

    emotion_type: Literal["smart"] = Field(
        default="smart",
        description="Must be 'smart' for context-aware emotion inference",
    )
    previous_text: Optional[str] = Field(
        default=None,
        description="Text that comes BEFORE the main text (max 2000 chars)",
        max_length=2000,
    )
    next_text: Optional[str] = Field(
        default=None,
        description="Text that comes AFTER the main text (max 2000 chars)",
        max_length=2000,
    )


# Union type for all prompt types
TTSPrompt = Union[Prompt, PresetPrompt, SmartPrompt]


class Output(BaseModel):
    volume: Optional[int] = Field(default=100, ge=0, le=200)
    audio_pitch: Optional[int] = Field(default=0, ge=-12, le=12)
    audio_tempo: Optional[float] = Field(default=1.0, ge=0.5, le=2.0)
    audio_format: Optional[str] = Field(
        default="wav", description="Audio format", examples=["wav", "mp3"]
    )


class TTSRequest(BaseModel):
    model_config = ConfigDict(json_schema_extra={"exclude_none": True})

    voice_id: str = Field(
        description="Voice ID", examples=["tc_62a8975e695ad26f7fb514d1"]
    )
    text: str = Field(description="Text", examples=["Hello. How are you?"])
    model: TTSModel = Field(description="Voice model name", examples=["ssfm-v21"])
    language: Optional[Union[LanguageCode, str]] = Field(
        None, description="Language code (ISO 639-3)", examples=["eng"]
    )
    prompt: Optional[TTSPrompt] = None
    output: Optional[Output] = None
    seed: Optional[int] = None


class TTSResponse(BaseModel):
    audio_data: bytes
    duration: float
    format: str = "wav"
