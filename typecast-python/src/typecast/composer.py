from __future__ import annotations

import math
import re
from dataclasses import dataclass
from typing import Callable, Literal, Optional, Union

from .models import LanguageCode, Output, TTSModel, TTSPrompt, TTSRequest, TTSResponse

_PAUSE_TOKEN = re.compile(r"<\|(\d+(?:\.\d+)?)s\|>")


@dataclass(frozen=True)
class _TextPart:
    kind: Literal["text"]
    text: str


@dataclass(frozen=True)
class _PausePart:
    kind: Literal["pause"]
    seconds: float


@dataclass(frozen=True)
class _SpeechPart:
    text: str
    settings: "_ComposerSettings"


@dataclass(frozen=True)
class _ComposerSettings:
    voice_id: Optional[str] = None
    model: Optional[Union[TTSModel, str]] = None
    language: Optional[Union[LanguageCode, str]] = None
    prompt: Optional[TTSPrompt] = None
    output: Optional[Output] = None
    seed: Optional[int] = None


class SpeechComposer:
    def __init__(self, compose: Callable[[list[dict]], TTSResponse]):
        self._compose = compose
        self._defaults = _ComposerSettings()
        self._parts: list[Union[_SpeechPart, _PausePart]] = []

    def defaults(
        self,
        *,
        voice_id: Optional[str] = None,
        model: Optional[Union[TTSModel, str]] = None,
        language: Optional[Union[LanguageCode, str]] = None,
        prompt: Optional[TTSPrompt] = None,
        output: Optional[Output] = None,
        seed: Optional[int] = None,
    ) -> "SpeechComposer":
        self._defaults = _merge_settings(
            self._defaults,
            _ComposerSettings(
                voice_id=voice_id,
                model=model,
                language=language,
                prompt=prompt,
                output=output,
                seed=seed,
            ),
        )
        return self

    def say(
        self,
        text: str,
        *,
        voice_id: Optional[str] = None,
        model: Optional[Union[TTSModel, str]] = None,
        language: Optional[Union[LanguageCode, str]] = None,
        prompt: Optional[TTSPrompt] = None,
        output: Optional[Output] = None,
        seed: Optional[int] = None,
    ) -> "SpeechComposer":
        settings = _merge_settings(
            self._defaults,
            _ComposerSettings(
                voice_id=voice_id,
                model=model,
                language=language,
                prompt=prompt,
                output=output,
                seed=seed,
            ),
        )
        self._parts.append(_SpeechPart(text=text, settings=settings))
        return self

    def pause(self, seconds: float) -> "SpeechComposer":
        """Insert silence between speech segments.

        Args:
            seconds: Duration in seconds. Use 0.3 for 300 ms, 3 for 3 seconds.
        """
        if not math.isfinite(seconds) or seconds <= 0:
            raise ValueError("pause seconds must be greater than 0")
        self._parts.append(_PausePart(kind="pause", seconds=seconds))
        return self

    def generate(self) -> TTSResponse:
        plan = self._build_plan()
        if not any(isinstance(part, _SpeechPart) for part in plan):
            raise ValueError("at least one speech segment is required")

        output_format = (
            self._defaults.output.audio_format if self._defaults.output else "wav"
        )
        if output_format not in ("wav", "mp3"):
            raise ValueError(
                f"unsupported composed speech output format: {output_format}"
            )

        segments: list[dict] = []
        for part in plan:
            if isinstance(part, _PausePart):
                segments.append({"type": "pause", "duration_seconds": part.seconds})
                continue
            request = _settings_to_request(part.text, part.settings, output_format)
            segments.append({"type": "tts", **request.model_dump(exclude_none=True)})
        return self._compose(segments)

    def _build_plan(self) -> list[Union[_SpeechPart, _PausePart]]:
        plan: list[Union[_SpeechPart, _PausePart]] = []
        for part in self._parts:
            if isinstance(part, _PausePart):
                plan.append(part)
                continue
            for parsed in parse_pause_markup(part.text):
                if isinstance(parsed, _PausePart):
                    plan.append(parsed)
                    continue
                if not parsed.text.strip():
                    continue
                if not part.settings.voice_id:
                    raise ValueError(
                        "voice_id is required for composed speech segments"
                    )
                if not part.settings.model:
                    raise ValueError("model is required for composed speech segments")
                plan.append(_SpeechPart(text=parsed.text, settings=part.settings))
        return plan


def parse_pause_markup(text: str) -> list[Union[_TextPart, _PausePart]]:
    parts: list[Union[_TextPart, _PausePart]] = []
    last_index = 0
    for match in _PAUSE_TOKEN.finditer(text):
        if match.start() > last_index:
            parts.append(_TextPart(kind="text", text=text[last_index : match.start()]))
        parts.append(_PausePart(kind="pause", seconds=float(match.group(1))))
        last_index = match.end()
    if last_index < len(text):
        parts.append(_TextPart(kind="text", text=text[last_index:]))
    return parts


def _merge_settings(
    base: _ComposerSettings,
    override: _ComposerSettings,
) -> _ComposerSettings:
    return _ComposerSettings(
        voice_id=override.voice_id if override.voice_id is not None else base.voice_id,
        model=override.model if override.model is not None else base.model,
        language=override.language if override.language is not None else base.language,
        prompt=override.prompt if override.prompt is not None else base.prompt,
        output=_merge_output(base.output, override.output),
        seed=override.seed if override.seed is not None else base.seed,
    )


def _merge_output(
    base: Optional[Output], override: Optional[Output]
) -> Optional[Output]:
    if base is None and override is None:
        return None
    data = (
        base.model_dump(exclude_none=True, exclude_unset=True)
        if base is not None
        else {}
    )
    if override is not None:
        data.update(override.model_dump(exclude_none=True, exclude_unset=True))
    return Output(**data)


def _settings_to_request(
    text: str, settings: _ComposerSettings, output_format: str
) -> TTSRequest:
    output = _merge_output(settings.output, Output(audio_format=output_format))
    return TTSRequest(
        text=text,
        voice_id=settings.voice_id or "",
        model=settings.model
        if isinstance(settings.model, TTSModel)
        else TTSModel(settings.model),
        language=settings.language,
        prompt=settings.prompt,
        output=output,
        seed=settings.seed,
    )
