import pytest

from typecast.models import TTSRequest, TTSRequestStream, TTSRequestWithTimestamps


@pytest.mark.parametrize("request_type", [TTSRequest, TTSRequestStream, TTSRequestWithTimestamps])
@pytest.mark.parametrize("seed", [None, 0, 42])
def test_legacy_seed_is_not_serialized(request_type, seed):
    request = request_type(voice_id="tc_test", text="hello", model="ssfm-v30", seed=seed)
    assert "seed" not in request.model_dump(exclude_none=True)
    assert '"seed"' not in request.model_dump_json(exclude_none=True)

