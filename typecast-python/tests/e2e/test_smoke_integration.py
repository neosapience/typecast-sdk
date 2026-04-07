#!/usr/bin/env python3
"""
Integration smoke test for built package.
Tests actual API calls. Requires TYPECAST_API_KEY.
"""
import os
import sys
import tempfile

from dotenv import load_dotenv

# Load environment variables from .env file
load_dotenv()


def test_voice_list():
    """Test fetching voice list"""
    try:
        from typecast import Typecast

        cli = Typecast()
        voices = cli.voices()

        if len(voices) == 0:
            print("⚠ Voice list is empty")
            return False

        print(f"✓ Voice list fetched: {len(voices)} voices")
        return True
    except Exception as e:
        print(f"✗ Voice list failed: {e}")
        return False


def test_tts_generation():
    """Test actual TTS generation"""
    try:
        from typecast import Typecast
        from typecast.models import LanguageCode, TTSRequest

        cli = Typecast()
        response = cli.text_to_speech(
            TTSRequest(
                text="Hello, this is a smoke test.",
                model="ssfm-v21",
                voice_id="tc_62a8975e695ad26f7fb514d1",
                language=LanguageCode.ENG,
            )
        )

        # Verify response
        if not response.audio_data:
            print("✗ No audio data in response")
            return False

        if len(response.audio_data) < 1000:
            print(f"✗ Audio data too small: {len(response.audio_data)} bytes")
            return False

        # Save to temp file to verify it's valid
        with tempfile.NamedTemporaryFile(
            suffix=".wav", delete=True
        ) as tmp_file:
            tmp_file.write(response.audio_data)
            file_size = os.path.getsize(tmp_file.name)

            print(
                f"✓ TTS generation successful: {file_size:,} bytes, format: {response.format}"
            )
            return True

    except Exception as e:
        print(f"✗ TTS generation failed: {e}")
        import traceback

        traceback.print_exc()
        return False


def main():
    """Run integration smoke tests"""
    print("🎤 Running integration smoke tests...\n")

    # Check API key
    api_key = os.getenv("TYPECAST_API_KEY")
    if not api_key:
        print("⚠ TYPECAST_API_KEY not set, skipping integration tests")
        return 0

    tests = [test_voice_list, test_tts_generation]

    results = []
    for test in tests:
        results.append(test())
        print()

    if all(results):
        print("✅ All integration smoke tests passed!")
        return 0
    else:
        print("❌ Some integration smoke tests failed")
        return 1


if __name__ == "__main__":
    sys.exit(main())

