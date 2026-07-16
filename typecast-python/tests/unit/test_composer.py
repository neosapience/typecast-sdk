import pytest

from typecast.client import Typecast
from typecast.composer import parse_pause_markup
from typecast.models import Output, TTSResponse


@pytest.fixture
def client():
    return Typecast(host="https://dummy.example", api_key="test-key")


def test_compose_speech_uses_compose_api_once(client, mocker):
    response = TTSResponse(audio_data=b"mp3", duration=2.5, format="mp3")
    compose = mocker.patch.object(
        client, "compose_text_to_speech", return_value=response
    )
    actual = (
        client.compose_speech()
        .defaults(
            voice_id="voice-a", model="ssfm-v30", output=Output(audio_format="mp3")
        )
        .say("Hello<|0.3s|>world")
        .pause(1)
        .say("Again", voice_id="voice-b")
        .generate()
    )
    assert actual is response
    segments = compose.call_args.args[0]
    assert [
        (segment["type"], segment.get("text"), segment.get("duration_seconds"))
        for segment in segments
    ] == [
        ("tts", "Hello", None),
        ("pause", None, 0.3),
        ("tts", "world", None),
        ("pause", None, 1),
        ("tts", "Again", None),
    ]
    assert [
        segment["voice_id"] for segment in segments if segment["type"] == "tts"
    ] == ["voice-a", "voice-a", "voice-b"]
    assert all(
        segment["output"]["audio_format"] == "mp3"
        for segment in segments
        if segment["type"] == "tts"
    )


def test_compose_speech_validates_builder(client, mocker):
    compose = mocker.patch.object(client, "compose_text_to_speech")
    with pytest.raises(ValueError, match="greater than 0"):
        client.compose_speech().pause(0)
    with pytest.raises(ValueError, match="at least one speech"):
        client.compose_speech().generate()
    with pytest.raises(ValueError, match="voice_id"):
        client.compose_speech().defaults(model="ssfm-v30").say("Hello").generate()
    with pytest.raises(ValueError, match="model"):
        client.compose_speech().defaults(voice_id="voice").say("Hello").generate()
    compose.assert_not_called()


def test_parse_pause_markup_preserves_invalid_tokens():
    parts = parse_pause_markup("a<|0.5s|>b<|bad|>")
    assert [
        (part.kind, getattr(part, "text", None), getattr(part, "seconds", None))
        for part in parts
    ] == [("text", "a", None), ("pause", None, 0.5), ("text", "b<|bad|>", None)]
