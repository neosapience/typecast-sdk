from __future__ import annotations

import struct

import pytest

from typecast.client import Typecast
from typecast.composer import parse_pause_markup
from typecast.models import Output, TTSResponse


def make_wav(samples: list[int], sample_rate: int = 8000) -> bytes:
    data = b"".join(struct.pack("<h", sample) for sample in samples)
    return b"".join(
        [
            b"RIFF",
            struct.pack("<I", 36 + len(data)),
            b"WAVE",
            b"fmt ",
            struct.pack("<IHHIIHH", 16, 1, 1, sample_rate, sample_rate * 2, 2, 16),
            b"data",
            struct.pack("<I", len(data)),
            data,
        ]
    )


def read_pcm_samples(wav: bytes) -> list[int]:
    return [
        struct.unpack("<h", wav[offset : offset + 2])[0]
        for offset in range(44, len(wav), 2)
    ]


@pytest.fixture
def client():
    return Typecast(host="https://dummy.example", api_key="test-key")


def test_compose_speech_splits_pause_markup_and_merges_overrides(client, mocker):
    tts_mock = mocker.patch.object(
        client,
        "text_to_speech",
        side_effect=[
            TTSResponse(audio_data=make_wav([0, 1000, 0]), duration=0.0005, format="wav"),
            TTSResponse(audio_data=make_wav([0, 2000, 0]), duration=0.0005, format="wav"),
            TTSResponse(audio_data=make_wav([0, 3000, 0]), duration=0.0005, format="wav"),
        ],
    )

    response = (
        client.compose_speech()
        .defaults(
            model="ssfm-v30",
            voice_id="tc_voice_a",
            output=Output(audio_format="wav", audio_pitch=1, audio_tempo=1.0),
        )
        .say("Hello<|0.001s|><|abc|>")
        .pause(0.001)
        .say("World", voice_id="tc_voice_b", output=Output(audio_pitch=3))
        .generate()
    )

    assert response.format == "wav"
    assert response.duration == 19 / 8000
    assert read_pcm_samples(response.audio_data) == [
        1000,
        0, 0, 0, 0, 0, 0, 0, 0,
        2000,
        0, 0, 0, 0, 0, 0, 0, 0,
        3000,
    ]

    requests = [call.args[0] for call in tts_mock.call_args_list]
    assert [request.text for request in requests] == ["Hello", "<|abc|>", "World"]
    assert [request.voice_id for request in requests] == [
        "tc_voice_a",
        "tc_voice_a",
        "tc_voice_b",
    ]
    assert requests[0].output.audio_format == "wav"
    assert requests[1].output.audio_pitch == 1
    assert requests[2].output.audio_pitch == 3
    assert requests[2].output.audio_tempo == 1.0


def test_compose_speech_validates_state_before_network_calls(client, mocker):
    tts_mock = mocker.patch.object(client, "text_to_speech")

    with pytest.raises(ValueError, match="pause seconds must be greater than 0"):
        client.compose_speech().pause(0)
    with pytest.raises(ValueError, match="at least one speech segment is required"):
        client.compose_speech().generate()
    with pytest.raises(ValueError, match="voice_id is required"):
        client.compose_speech().defaults(model="ssfm-v30").say("Hello").generate()
    with pytest.raises(ValueError, match="model is required"):
        client.compose_speech().defaults(voice_id="tc_voice_a").say("Hello").generate()
    with pytest.raises(ValueError, match="unsupported composed speech output format"):
        (
            client.compose_speech()
            .defaults(model="ssfm-v30", voice_id="tc_voice_a", output=Output(audio_format="flac"))
            .say("Hello")
            .generate()
        )

    tts_mock.assert_not_called()


def test_compose_speech_rejects_pause_before_audio_exists(client):
    with pytest.raises(ValueError, match="pause cannot be the first composed part"):
        (
            client.compose_speech()
            .defaults(model="ssfm-v30", voice_id="tc_voice_a")
            .pause(0.001)
            .say("Hello")
            .generate()
        )


def test_compose_speech_skips_empty_text_parts(client, mocker):
    tts_mock = mocker.patch.object(
        client,
        "text_to_speech",
        return_value=TTSResponse(
            audio_data=make_wav([1000]),
            duration=0.0005,
            format="wav",
        ),
    )

    client.compose_speech().defaults(model="ssfm-v30", voice_id="tc_voice_a").say(
        "Hello<|0.001s|>   "
    ).generate()

    assert tts_mock.call_count == 1
    assert tts_mock.call_args.args[0].text == "Hello"


def test_compose_speech_rejects_invalid_wav_data(client, mocker):
    mocker.patch.object(
        client,
        "text_to_speech",
        return_value=TTSResponse(audio_data=b"not wav", duration=0, format="wav"),
    )

    with pytest.raises(ValueError, match="unsupported WAV data"):
        (
            client.compose_speech()
            .defaults(model="ssfm-v30", voice_id="tc_voice_a")
            .say("Hello")
            .generate()
        )


def test_compose_speech_rejects_unsupported_wav_format(client, mocker):
    stereo_wav = bytearray(make_wav([1000]))
    struct.pack_into("<H", stereo_wav, 22, 2)
    mocker.patch.object(
        client,
        "text_to_speech",
        return_value=TTSResponse(audio_data=bytes(stereo_wav), duration=0, format="wav"),
    )

    with pytest.raises(ValueError, match="only mono 16-bit PCM WAV is supported"):
        (
            client.compose_speech()
            .defaults(model="ssfm-v30", voice_id="tc_voice_a")
            .say("Hello")
            .generate()
        )


def test_compose_speech_rejects_missing_data_chunk(client, mocker):
    wav = bytearray(make_wav([1000]))
    wav[36:40] = b"JUNK"
    mocker.patch.object(
        client,
        "text_to_speech",
        return_value=TTSResponse(audio_data=bytes(wav), duration=0, format="wav"),
    )

    with pytest.raises(ValueError, match="unsupported WAV data"):
        (
            client.compose_speech()
            .defaults(model="ssfm-v30", voice_id="tc_voice_a")
            .say("Hello")
            .generate()
        )


def test_compose_speech_rejects_mismatched_wav_specs(client, mocker):
    mocker.patch.object(
        client,
        "text_to_speech",
        side_effect=[
            TTSResponse(audio_data=make_wav([1000], 8000), duration=0, format="wav"),
            TTSResponse(audio_data=make_wav([2000], 16000), duration=0, format="wav"),
        ],
    )

    with pytest.raises(ValueError, match="same PCM format"):
        (
            client.compose_speech()
            .defaults(model="ssfm-v30", voice_id="tc_voice_a")
            .say("Hello")
            .say("World")
            .generate()
        )


def test_compose_speech_mp3_requires_ffmpeg(client, mocker):
    mocker.patch.object(
        client,
        "text_to_speech",
        return_value=TTSResponse(audio_data=make_wav([1000]), duration=0, format="wav"),
    )

    with pytest.raises(ValueError, match="ffmpeg is required to encode composed speech as mp3"):
        (
            client.compose_speech()
            .defaults(
                model="ssfm-v30",
                voice_id="tc_voice_a",
                output=Output(audio_format="mp3"),
            )
            .say("Hello")
            .generate()
        )


def test_parse_pause_markup_handles_boundary_tokens():
    start_parts = parse_pause_markup("<|0.5s|>Hello")
    assert [part.kind for part in start_parts] == ["pause", "text"]
    assert start_parts[0].seconds == 0.5
    assert start_parts[1].text == "Hello"

    end_parts = parse_pause_markup("Hello<|1s|>")
    assert [part.kind for part in end_parts] == ["text", "pause"]
    assert end_parts[0].text == "Hello"
    assert end_parts[1].seconds == 1.0
