import pytest

from typecast.models.tts import (
    PresetPrompt,
    TTSModel,
    TTSRequest,
    TTSRequestStream,
    TTSRequestWithTimestamps,
)
from typecast.models.voices import ModelInfo, VoicesV2Filter


@pytest.mark.parametrize(
    "request_type", [TTSRequest, TTSRequestStream, TTSRequestWithTimestamps]
)
def test_v31_request_and_voice_models(request_type):
    request = request_type(
        voice_id="tc_test",
        text="hello",
        model=TTSModel.SSFM_V31,
        prompt=PresetPrompt(emotion_preset="normal"),
    )
    data = request.model_dump(mode="json", exclude_none=True)
    assert data["model"] == "ssfm-v31"
    assert data["prompt"]["emotion_type"] == "preset"
    assert (
        ModelInfo(version="ssfm-v31", emotions=["normal"]).version == TTSModel.SSFM_V31
    )
    assert (
        VoicesV2Filter(model=TTSModel.SSFM_V31).model_dump(mode="json")["model"]
        == "ssfm-v31"
    )
