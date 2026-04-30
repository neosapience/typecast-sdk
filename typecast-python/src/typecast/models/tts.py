from enum import Enum
from typing import Literal, Optional, Union

from pydantic import BaseModel, ConfigDict, Field, model_validator


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
    volume: Optional[int] = Field(
        default=100,
        ge=0,
        le=200,
        description="Volume (0-200). Cannot be used together with target_lufs.",
    )
    target_lufs: Optional[float] = Field(
        default=None,
        ge=-70.0,
        le=0.0,
        description="Target loudness in LUFS for absolute loudness normalization (-70 to 0). Cannot be used together with volume.",
    )
    audio_pitch: Optional[int] = Field(default=0, ge=-12, le=12)
    audio_tempo: Optional[float] = Field(default=1.0, ge=0.5, le=2.0)
    audio_format: Optional[str] = Field(
        default="wav", description="Audio format", examples=["wav", "mp3"]
    )

    @model_validator(mode="before")
    @classmethod
    def check_volume_and_target_lufs(cls, data: dict) -> dict:
        if isinstance(data, dict):
            target_lufs = data.get("target_lufs")
            volume = data.get("volume")
            volume_explicitly_set = "volume" in data
            if target_lufs is not None and volume is not None and volume_explicitly_set:
                raise ValueError("volume and target_lufs cannot be used together")
            if target_lufs is not None and not volume_explicitly_set:
                data["volume"] = None
        return data


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


class OutputStream(BaseModel):
    """Audio output settings for streaming mode.

    Streaming mode does not support `volume` or `target_lufs` because the
    server has to commit each chunk before the full waveform is known.
    Passing either field raises a validation error so misuse fails fast.
    """

    model_config = ConfigDict(extra="forbid")

    audio_pitch: Optional[int] = Field(default=0, ge=-12, le=12)
    audio_tempo: Optional[float] = Field(default=1.0, ge=0.5, le=2.0)
    audio_format: Optional[str] = Field(
        default="wav", description="Audio format", examples=["wav", "mp3"]
    )


class TTSRequestStream(BaseModel):
    """Request body for `POST /v1/text-to-speech/stream`.

    Mirrors `TTSRequest` but uses `OutputStream` (no volume / target_lufs).
    """

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
    output: Optional[OutputStream] = None
    seed: Optional[int] = None


class AlignmentSegmentWord(BaseModel):
    """A single word-level alignment segment between transcript and audio."""

    text: str = Field(description="Text fragment (with attached punctuation).")
    start: float = Field(description="Start time in seconds.")
    end: float = Field(description="End time in seconds.")


class AlignmentSegmentCharacter(BaseModel):
    """A single character-level alignment segment between transcript and audio."""

    text: str = Field(description="Character fragment (with punctuation/whitespace).")
    start: float = Field(description="Start time in seconds.")
    end: float = Field(description="End time in seconds.")


class TTSRequestWithTimestamps(BaseModel):
    """Request body for `POST /v1/text-to-speech/with-timestamps`.

    Mirrors `TTSRequest` (voice_id, text, model, language, prompt, output, seed).
    The optional `granularity` query parameter is *not* part of this body — pass
    it as a method argument to `text_to_speech_with_timestamps()`.
    """

    model_config = ConfigDict(json_schema_extra={"exclude_none": True})

    voice_id: str = Field(
        description="Voice ID", examples=["tc_62a8975e695ad26f7fb514d1"]
    )
    text: str = Field(description="Text", examples=["Hello. How are you?"])
    model: TTSModel = Field(description="Voice model name", examples=["ssfm-v30"])
    language: Optional[Union[LanguageCode, str]] = Field(
        None, description="Language code (ISO 639-3)", examples=["eng"]
    )
    prompt: Optional[TTSPrompt] = None
    output: Optional[Output] = None
    seed: Optional[int] = None


# --- timestamp captioning helpers (module-level, shared by SRT/VTT) ---

_SENTENCE_TERMINATORS = (".", "?", "!", "。", "？", "！")
_MAX_CAPTION_SECONDS = 7.0
_MAX_CAPTION_CHARS = 42


def _segments_for_captioning(words, characters):
    """Pick which segment list to use, returning (segments, word_mode) tuple.

    - words with >= 2 entries -> words (word_mode=True: join parts with space)
    - else if characters with >= 1 entry -> characters (word_mode=False: concat directly)
    - single-entry words with no characters -> words (word_mode=True, one cue)
    - else -> ValueError
    """
    if words and len(words) >= 2:
        return words, True
    if characters and len(characters) >= 1:
        return characters, False
    if words and len(words) == 1 and not characters:
        return words, True  # English single-cue is still valid
    raise ValueError("no alignment segments to caption from")


def _group_into_cues(segments, word_mode: bool = False):
    """Group segments into caption cues using shared rules:
    - Split on sentence terminator at end of segment text.
    - Split BEFORE appending if adding the segment would push the cue past 7.0s or 42 chars (hard cap).

    word_mode=True: parts are joined with a single space.
    word_mode=False: parts are concatenated directly.

    Returns list[(text, start, end)] tuples.
    """
    cues = []
    cur_text_parts = []
    cur_start = None
    last_end = None

    def _joined():
        if word_mode:
            return " ".join(cur_text_parts).strip()
        return "".join(cur_text_parts).strip()

    def _flush(end_time):
        text = _joined()
        if text:
            cues.append((text, cur_start, end_time))

    for seg in segments:
        if cur_text_parts and cur_start is not None and last_end is not None:
            if word_mode:
                would_be_text = " ".join([*cur_text_parts, seg.text]).strip()
            else:
                would_be_text = "".join([*cur_text_parts, seg.text]).strip()
            would_exceed_seconds = (seg.end - cur_start) > _MAX_CAPTION_SECONDS
            would_exceed_chars = len(would_be_text) > _MAX_CAPTION_CHARS
            if would_exceed_seconds or would_exceed_chars:
                _flush(last_end)
                cur_text_parts = []
                cur_start = None

        if cur_start is None:
            cur_start = seg.start
        cur_text_parts.append(seg.text)
        last_end = seg.end

        ends_in_sentence = seg.text.rstrip().endswith(_SENTENCE_TERMINATORS)
        if ends_in_sentence:
            _flush(seg.end)
            cur_text_parts = []
            cur_start = None

    if cur_text_parts and last_end is not None:
        _flush(last_end)
    return cues


def _format_srt_time(seconds: float) -> str:
    total_ms = int(round(seconds * 1000))
    hh, rem = divmod(total_ms, 3600 * 1000)
    mm, rem = divmod(rem, 60 * 1000)
    ss, ms = divmod(rem, 1000)
    return f"{hh:02d}:{mm:02d}:{ss:02d},{ms:03d}"


def _format_vtt_time(seconds: float) -> str:
    """Format time for WebVTT format: HH:MM:SS.mmm (dot decimal, not comma)."""
    total_ms = int(round(seconds * 1000))
    hh, rem = divmod(total_ms, 3600 * 1000)
    mm, rem = divmod(rem, 60 * 1000)
    ss, ms = divmod(rem, 1000)
    return f"{hh:02d}:{mm:02d}:{ss:02d}.{ms:03d}"


class TTSWithTimestampsResponse(BaseModel):
    """Response payload for `POST /v1/text-to-speech/with-timestamps`.

    Contains base64-encoded audio plus optional word/character alignment arrays.
    Helper methods (`save_audio()`, `to_srt()`, `to_vtt()`) are added in
    subsequent tasks.
    """

    audio: str = Field(description="Base64-encoded audio bytes.")
    audio_format: Literal["wav", "mp3"] = Field(description="Audio encoding format.")
    audio_duration: float = Field(description="Length of audio in seconds.")
    words: Optional[list[AlignmentSegmentWord]] = Field(
        default=None,
        description="Word-level timestamps; null when granularity=char.",
    )
    characters: Optional[list[AlignmentSegmentCharacter]] = Field(
        default=None,
        description="Character-level timestamps; null when granularity=word.",
    )

    @property
    def audio_bytes(self) -> bytes:
        """Return decoded audio bytes from the base64 `audio` field."""
        import base64
        return base64.b64decode(self.audio)

    def save_audio(self, path: str) -> None:
        """Write decoded audio bytes to `path`."""
        with open(path, "wb") as f:
            f.write(self.audio_bytes)

    def to_srt(self) -> str:
        """Return SRT-formatted caption string for this TTS response.

        Uses word-level segments when words has >= 2 entries; falls back to
        character-level segments otherwise (e.g. jpn/zho collapsed words).
        Cues are split on sentence terminators (. ? ! 。 ？ ！) or when a cue
        would exceed 7.0 seconds or 42 characters.
        """
        segments, word_mode = _segments_for_captioning(self.words, self.characters)
        cues = _group_into_cues(segments, word_mode=word_mode)
        if not cues:
            raise ValueError("no alignment segments to caption from")
        lines = []
        for idx, (text, start, end) in enumerate(cues, start=1):
            lines.append(str(idx))
            lines.append(f"{_format_srt_time(start)} --> {_format_srt_time(end)}")
            lines.append(text)
            lines.append("")
        return "\n".join(lines) + "\n"

    def to_vtt(self) -> str:
        """Return WebVTT-formatted caption string for this TTS response.

        Uses word-level segments when words has >= 2 entries; falls back to
        character-level segments otherwise (e.g. jpn/zho collapsed words).
        Cues are split on sentence terminators (. ? ! 。 ？ ！) or when a cue
        would exceed 7.0 seconds or 42 characters.
        """
        segments, word_mode = _segments_for_captioning(self.words, self.characters)
        cues = _group_into_cues(segments, word_mode=word_mode)
        if not cues:
            raise ValueError("no alignment segments to caption from")
        lines = ["WEBVTT", ""]
        for text, start, end in cues:
            lines.append(f"{_format_vtt_time(start)} --> {_format_vtt_time(end)}")
            lines.append(text)
            lines.append("")
        return "\n".join(lines) + "\n"
