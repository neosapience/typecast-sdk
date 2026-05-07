"""Unit tests for quick voice cloning DX."""
import io
import json
from pathlib import Path

import pytest

from typecast import Typecast
from typecast._voice_clone import validate_clone_inputs, CLONING_MAX_FILE_SIZE
from typecast.exceptions import NotFoundError
from typecast.models import CustomVoice


FIXTURE_DIR = Path(__file__).resolve().parents[2] / ".." / "test-fixtures" / "quick-cloning"


def _load_fixture(name: str) -> dict:
    return json.loads((FIXTURE_DIR / name).read_text())

def test_custom_voice_parses_response():
    payload = {"voice_id": "uc_64a1b2c3d4e5f6a7b8c9d0e1", "name": "demo", "model": "ssfm-v30"}
    voice = CustomVoice.model_validate(payload)
    assert voice.voice_id == "uc_64a1b2c3d4e5f6a7b8c9d0e1"
    assert voice.name == "demo"
    assert voice.model == "ssfm-v30"


def test_validate_rejects_file_too_large():
    big = b"\x00" * (CLONING_MAX_FILE_SIZE + 1)
    with pytest.raises(ValueError, match="audio file exceeds 25MB limit"):
        validate_clone_inputs(big, "demo")


def test_validate_rejects_empty_name():
    with pytest.raises(ValueError, match="name must be 1-30 characters"):
        validate_clone_inputs(b"\x00" * 1024, "")


def test_validate_rejects_long_name():
    with pytest.raises(ValueError, match="name must be 1-30 characters"):
        validate_clone_inputs(b"\x00" * 1024, "x" * 31)


def test_validate_rejects_missing_path(tmp_path):
    missing = tmp_path / "nope.wav"
    with pytest.raises(FileNotFoundError):
        validate_clone_inputs(missing, "demo")


def test_validate_accepts_path(tmp_path):
    p = tmp_path / "ok.wav"
    p.write_bytes(b"\x00" * 1024)
    audio_bytes, filename = validate_clone_inputs(p, "demo")
    assert audio_bytes == b"\x00" * 1024
    assert filename == "ok.wav"


def test_validate_accepts_bytes_with_default_filename():
    audio_bytes, filename = validate_clone_inputs(b"\x00" * 1024, "demo")
    assert audio_bytes == b"\x00" * 1024
    assert filename == "audio.wav"


def test_validate_accepts_file_object():
    buf = io.BytesIO(b"\x00" * 2048)
    buf.name = "foo.mp3"
    audio_bytes, filename = validate_clone_inputs(buf, "demo")
    assert audio_bytes == b"\x00" * 2048
    assert filename == "foo.mp3"


def test_clone_voice_returns_custom_voice(mocker):
    fixture = _load_fixture("success_v30.json")
    client = Typecast(api_key="test-key")
    mock_response = mocker.Mock()
    mock_response.status_code = 200
    mock_response.json.return_value = fixture
    mocker.patch.object(client.session, "post", return_value=mock_response)

    voice = client.clone_voice(audio=b"\x00" * 2048, name="demo", model="ssfm-v30")

    assert voice.voice_id == fixture["voice_id"]
    assert voice.name == fixture["name"]
    assert voice.model == fixture["model"]


def test_clone_voice_sends_multipart_body(mocker):
    fixture = _load_fixture("success_v21.json")
    client = Typecast(api_key="test-key")
    mock_response = mocker.Mock()
    mock_response.status_code = 200
    mock_response.json.return_value = fixture
    post_mock = mocker.patch.object(client.session, "post", return_value=mock_response)

    client.clone_voice(audio=b"\x00" * 1024, name="demo", model="ssfm-v21")

    call_kwargs = post_mock.call_args.kwargs
    assert call_kwargs["data"] == {"name": "demo", "model": "ssfm-v21"}
    file_part = call_kwargs["files"]["file"]
    assert file_part[0] == "audio.wav"
    assert file_part[1] == b"\x00" * 1024
    assert file_part[2] in ("audio/wav", "application/octet-stream")
    assert "/v1/voices/clone" in post_mock.call_args.args[0]


def test_clone_voice_pre_validates_size(mocker):
    client = Typecast(api_key="test-key")
    post_mock = mocker.patch.object(client.session, "post")
    big = b"\x00" * (CLONING_MAX_FILE_SIZE + 1)

    with pytest.raises(ValueError, match="audio file exceeds 25MB limit"):
        client.clone_voice(audio=big, name="demo", model="ssfm-v30")

    post_mock.assert_not_called()


def test_delete_voice_returns_none(mocker):
    client = Typecast(api_key="test-key")
    mock_response = mocker.Mock()
    mock_response.status_code = 204
    mock_response.text = ""
    delete_mock = mocker.patch.object(client.session, "delete", return_value=mock_response)

    result = client.delete_voice("uc_64a1b2c3d4e5f6a7b8c9d0e1")

    assert result is None
    args = delete_mock.call_args.args
    assert "/v1/voices/uc_64a1b2c3d4e5f6a7b8c9d0e1" in args[0]


def test_delete_voice_raises_on_404(mocker):
    client = Typecast(api_key="test-key")
    mock_response = mocker.Mock()
    mock_response.status_code = 404
    mock_response.text = '{"detail": {"code": "NOT_FOUND", "message": "voice not found"}}'
    mocker.patch.object(client.session, "delete", return_value=mock_response)

    with pytest.raises(NotFoundError):
        client.delete_voice("uc_xxx")
