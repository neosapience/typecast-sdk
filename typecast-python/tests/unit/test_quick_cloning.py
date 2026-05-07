"""Unit tests for quick voice cloning DX."""
from typecast.models import CustomVoice


def test_custom_voice_parses_response():
    payload = {"voice_id": "uc_64a1b2c3d4e5f6a7b8c9d0e1", "name": "demo", "model": "ssfm-v30"}
    voice = CustomVoice.model_validate(payload)
    assert voice.voice_id == "uc_64a1b2c3d4e5f6a7b8c9d0e1"
    assert voice.name == "demo"
    assert voice.model == "ssfm-v30"
