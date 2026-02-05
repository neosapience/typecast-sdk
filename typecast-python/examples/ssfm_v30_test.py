"""
Test script for ssfm-v30 model support

Usage:
    TYPECAST_API_HOST=<your_api_host> TYPECAST_API_KEY=<your_api_key> python examples/ssfm_v30_test.py

Or set in .env file:
    TYPECAST_API_HOST=<your_api_host>
    TYPECAST_API_KEY=<your_api_key>
"""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src"))

from typecast import Typecast
from typecast.models import (
    TTSModel,
    TTSRequest,
    Prompt,
    PresetPrompt,
    SmartPrompt,
    VoicesV2Filter,
)


def test_voices_v2(client: Typecast) -> None:
    print("1. Testing /v2/voices API...")
    voices = client.voices_v2()
    print(f"   Found {len(voices)} voices (V2)")

    if voices:
        sample = voices[0]
        print(f"   Sample: {sample.voice_name} ({sample.voice_id})")
        print(f"   Models: {', '.join(m.version.value for m in sample.models)}")
        gender = sample.gender.value if sample.gender else "N/A"
        age = sample.age.value if sample.age else "N/A"
        print(f"   Gender: {gender}, Age: {age}")


def test_voices_v2_with_filter(client: Typecast) -> list:
    print("\n2. Testing /v2/voices with filter (ssfm-v30)...")
    filter_obj = VoicesV2Filter(model=TTSModel.SSFM_V30)
    voices = client.voices_v2(filter=filter_obj)
    print(f"   Found {len(voices)} voices supporting ssfm-v30")
    return voices


def test_preset_prompt(client: Typecast, voice_id: str) -> None:
    print("\n3. Testing TTS with PresetPrompt (ssfm-v30)...")
    request = TTSRequest(
        voice_id=voice_id,
        text="Hello! This is a test using preset emotion control.",
        model=TTSModel.SSFM_V30,
        language="eng",
        prompt=PresetPrompt(
            emotion_type="preset",
            emotion_preset="happy",
            emotion_intensity=1.5,
        ),
    )
    response = client.text_to_speech(request)

    with open("output_preset_v30.wav", "wb") as f:
        f.write(response.audio_data)
    print(f"   Success! Duration: {response.duration}s, Format: {response.format}")
    print("   Saved: output_preset_v30.wav")


def test_smart_prompt(client: Typecast, voice_id: str) -> None:
    print("\n4. Testing TTS with SmartPrompt (ssfm-v30)...")
    request = TTSRequest(
        voice_id=voice_id,
        text="Everything is so incredibly perfect that I feel like I am dreaming.",
        model=TTSModel.SSFM_V30,
        language="eng",
        prompt=SmartPrompt(
            emotion_type="smart",
            previous_text="I just got the best news ever!",
            next_text="I cannot wait to share this with everyone!",
        ),
    )
    response = client.text_to_speech(request)

    with open("output_smart_v30.wav", "wb") as f:
        f.write(response.audio_data)
    print(f"   Success! Duration: {response.duration}s, Format: {response.format}")
    print("   Saved: output_smart_v30.wav")


def test_backward_compatibility(client: Typecast) -> None:
    print("\n5. Testing backward compatibility (ssfm-v21)...")
    voices = client.voices(model="ssfm-v21")
    print(f"   Found {len(voices)} voices (V1 API)")

    if voices:
        request = TTSRequest(
            voice_id=voices[0].voice_id,
            text="This is a backward compatibility test.",
            model=TTSModel.SSFM_V21,
            language="eng",
            prompt=Prompt(emotion_preset="normal", emotion_intensity=1.0),
        )
        response = client.text_to_speech(request)
        print(f"   Success! Duration: {response.duration}s")


def main() -> None:
    host = os.environ.get("TYPECAST_API_HOST", "https://api.typecast.ai")
    api_key = os.environ.get("TYPECAST_API_KEY", "")
    client = Typecast(host=host, api_key=api_key)

    print("=== Testing ssfm-v30 Support ===\n")

    try:
        test_voices_v2(client)

        v30_voices = test_voices_v2_with_filter(client)
        v30_voice = next(
            (v for v in v30_voices if any(m.version == TTSModel.SSFM_V30 for m in v.models)),
            None,
        )

        if v30_voice:
            print(f"   Using: {v30_voice.voice_name} ({v30_voice.voice_id})")
            test_preset_prompt(client, v30_voice.voice_id)
            test_smart_prompt(client, v30_voice.voice_id)
        else:
            print("   No ssfm-v30 voice found, skipping TTS tests")

        test_backward_compatibility(client)
    except Exception as e:
        print(f"Test failed: {e}")

    print("\n=== Tests completed ===")


if __name__ == "__main__":
    main()
