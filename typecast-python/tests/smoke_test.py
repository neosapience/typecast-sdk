#!/usr/bin/env python3
"""
Smoke test for built package.
Tests basic functionality without depending on API key.
"""
import sys


def test_imports():
    """Test all public imports"""
    try:
        from typecast import (
            AsyncTypecast,
            Error,
            LanguageCode,
            Output,
            Prompt,
            Typecast,
            TTSRequest,
            TTSResponse,
            VoicesResponse,
            WebSocketMessage,
        )

        print("✓ All imports successful")
        return True
    except ImportError as e:
        print(f"✗ Import failed: {e}")
        return False


def test_client_initialization():
    """Test client can be initialized"""
    try:
        from typecast import Typecast

        # Initialize without API key (should not fail at construction)
        cli = Typecast(api_key="test-key")
        print("✓ Client initialization successful")
        return True
    except Exception as e:
        print(f"✗ Client initialization failed: {e}")
        return False


def test_model_creation():
    """Test models can be created"""
    try:
        from typecast.models import LanguageCode, Output, Prompt, TTSRequest

        request = TTSRequest(
            text="Test",
            model="ssfm-v21",
            voice_id="tc_test",
            language=LanguageCode.ENG,
            prompt=Prompt(emotion_preset="happy"),
            output=Output(audio_format="wav"),
        )
        print("✓ Model creation successful")
        return True
    except Exception as e:
        print(f"✗ Model creation failed: {e}")
        return False


def main():
    """Run all smoke tests"""
    print("🔍 Running smoke tests...\n")

    tests = [test_imports, test_client_initialization, test_model_creation]

    results = []
    for test in tests:
        results.append(test())
        print()

    if all(results):
        print("✅ All smoke tests passed!")
        return 0
    else:
        print("❌ Some smoke tests failed")
        return 1


if __name__ == "__main__":
    sys.exit(main())

