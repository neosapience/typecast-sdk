import pytest
from pydantic import ValidationError
from typecast.models.tts import Output, OutputStream, TTSRequest, TTSRequestStream
from typecast.composer import _merge_output


@pytest.mark.parametrize("output_type", [Output, OutputStream])
def test_remove_silence_contract(output_type):
    for value in [None, 0, 300, 1000]:
        output = output_type(remove_silence_ms=value)
        data = output.model_dump(exclude_none=True)
        if value is None:
            assert "remove_silence_ms" not in data
        else:
            assert data["remove_silence_ms"] == value
    for value in [True, False, "100", 1.5, 100.0, -1, 1001]:
        with pytest.raises(ValidationError):
            output_type(remove_silence_ms=value)


def test_nested_requests_and_composer_preserve_zero():
    for request_type, output_type in [
        (TTSRequest, Output),
        (TTSRequestStream, OutputStream),
    ]:
        request = request_type(
            voice_id="test",
            text="test",
            model="ssfm-v30",
            output=output_type(remove_silence_ms=0),
        )
        assert request.model_dump(exclude_none=True)["output"]["remove_silence_ms"] == 0
    assert (
        _merge_output(
            Output(remove_silence_ms=300), Output(remove_silence_ms=0)
        ).remove_silence_ms
        == 0
    )
