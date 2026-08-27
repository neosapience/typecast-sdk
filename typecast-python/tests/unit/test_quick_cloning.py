"""Unit tests for instant cloning DX."""
import io
import json
from pathlib import Path

import pytest
from aioresponses import aioresponses

from typecast import Typecast
from typecast._voice_clone import CLONING_MAX_FILE_SIZE, validate_clone_inputs
from typecast.async_client import AsyncTypecast
from typecast.exceptions import InternalServerError, NotFoundError
from typecast.models import CustomVoice, TTSModel, VoicesV2Filter

FIXTURE_DIR = Path(__file__).resolve().parents[2] / ".." / "test-fixtures" / "quick-cloning"


def _load_fixture(name: str) -> dict:
    return json.loads((FIXTURE_DIR / name).read_text())

def test_custom_voice_parses_response():
    payload = {"voice_id": "uc_64a1b2c3d4e5f6a7b8c9d0e1", "name": "demo", "model": "ssfm-v30"}
    voice = CustomVoice.model_validate(payload)
    assert voice.voice_id == "uc_64a1b2c3d4e5f6a7b8c9d0e1"
    assert voice.name == "demo"
    assert voice.model == "ssfm-v30"


CUSTOM_VOICE = {
    "voice_id": "uc_64a1b2c3d4e5f6a7b8c9d0e1",
    "name": "demo",
    "model": "ssfm-v30",
    "source": "professional",
    "status": "processing",
}

V3_VOICE = {
    "voice_id": "tc_v3",
    "voice_name": {"eng": "Voice", "kor": "보이스"},
    "models": [{"version": "ssfm-v30", "emotions": ["normal"]}],
    "voice_type": "original",
}


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
    assert file_part[2] == "audio/wav"
    assert "/v1/custom-voices/instant-clone" in post_mock.call_args.args[0]


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
    assert "/v1/custom-voices/uc_64a1b2c3d4e5f6a7b8c9d0e1" in args[0]


def test_delete_voice_raises_on_404(mocker):
    client = Typecast(api_key="test-key")
    mock_response = mocker.Mock()
    mock_response.status_code = 404
    mock_response.text = '{"detail": {"code": "NOT_FOUND", "message": "voice not found"}}'
    mocker.patch.object(client.session, "delete", return_value=mock_response)

    with pytest.raises(NotFoundError):
        client.delete_voice("uc_xxx")


def test_create_professional_voice_returns_queued_voice(mocker):
    client = Typecast(api_key="test-key")
    response = mocker.Mock(status_code=202)
    response.json.return_value = CUSTOM_VOICE
    post = mocker.patch.object(client.session, "post", return_value=response)

    voice = client.create_professional_voice(
        b"\x00" * 1024, "demo", "en", "ssfm-v30"
    )

    assert voice.status == "processing"
    assert "/v1/custom-voices/professional-clone" in post.call_args.args[0]
    assert post.call_args.kwargs["data"] == {
        "name": "demo", "language": "en", "model": "ssfm-v30"
    }


def test_get_custom_voices_returns_owned_voices(mocker):
    client = Typecast(api_key="test-key")
    response = mocker.Mock(status_code=200)
    response.json.return_value = [CUSTOM_VOICE]
    get = mocker.patch.object(client.session, "get", return_value=response)

    voices = client.get_custom_voices()

    assert voices[0].source == "professional"
    get.assert_called_once_with(f"{client.host}/v1/custom-voices", headers=None)


def test_get_custom_voice_returns_clone_status(mocker):
    client = Typecast(api_key="test-key")
    response = mocker.Mock(status_code=200)
    response.json.return_value = CUSTOM_VOICE
    get = mocker.patch.object(client.session, "get", return_value=response)

    voice = client.get_custom_voice(CUSTOM_VOICE["voice_id"])

    assert voice.voice_id == CUSTOM_VOICE["voice_id"]
    assert get.call_args.args[0].endswith(CUSTOM_VOICE["voice_id"])


@pytest.mark.parametrize("method,args", [
    ("create_professional_voice", (b"\x00" * 1024, "demo", "en", "ssfm-v30")),
    ("get_custom_voices", ()),
    ("get_custom_voice", (CUSTOM_VOICE["voice_id"],)),
])
def test_custom_voice_endpoints_propagate_errors(mocker, method, args):
    client = Typecast(api_key="test-key")
    response = mocker.Mock(status_code=500, text="boom")
    mocker.patch.object(client.session, "post" if method == "create_professional_voice" else "get", return_value=response)

    with pytest.raises(InternalServerError):
        getattr(client, method)(*args)


def test_v3_voice_endpoints_support_filters_and_errors(mocker):
    client = Typecast(api_key="test-key")
    success = mocker.Mock(status_code=200)
    success.json.return_value = [V3_VOICE]
    get = mocker.patch.object(client.session, "get", return_value=success)

    assert client.voices_v3(VoicesV2Filter(model=TTSModel.SSFM_V30))[0].voice_id == "tc_v3"
    assert get.call_args.kwargs["params"] == {"model": "ssfm-v30"}

    failure = mocker.Mock(status_code=500, text="boom")
    get.return_value = failure
    with pytest.raises(InternalServerError):
        client.voices_v3()
    with pytest.raises(InternalServerError):
        client.voice_v3("tc_v3")


ASYNC_HOST = "https://dummy.example"


async def test_async_clone_voice_returns_custom_voice():
    fixture = _load_fixture("success_v30.json")
    with aioresponses() as m:
        m.post(f"{ASYNC_HOST}/v1/custom-voices/instant-clone", status=200, payload=fixture)
        async with AsyncTypecast(host=ASYNC_HOST, api_key="test-key") as client:
            voice = await client.clone_voice(
                audio=b"\x00" * 1024, name="demo", model="ssfm-v30"
            )
            assert voice.voice_id == fixture["voice_id"]
            assert voice.name == fixture["name"]
            assert voice.model == fixture["model"]


async def test_async_delete_voice_returns_none():
    with aioresponses() as m:
        m.delete(f"{ASYNC_HOST}/v1/custom-voices/uc_xxx", status=204)
        async with AsyncTypecast(host=ASYNC_HOST, api_key="test-key") as client:
            result = await client.delete_voice("uc_xxx")
            assert result is None


# --- Coverage gap closers ---


def test_validate_rejects_unsupported_audio_type():
    with pytest.raises(TypeError, match="audio must be"):
        validate_clone_inputs(12345, "demo")  # type: ignore[arg-type]


def test_validate_strips_directory_from_file_object_name(tmp_path):
    target = tmp_path / "sub" / "voice.mp3"
    target.parent.mkdir()
    target.write_bytes(b"\x00" * 1024)
    with target.open("rb") as fh:
        audio_bytes, filename = validate_clone_inputs(fh, "demo")
    assert audio_bytes == b"\x00" * 1024
    assert filename == "voice.mp3"  # basename, not full path


def test_validate_accepts_file_object_returning_bytearray():
    class _BytearrayReader:
        # `audio.read()` legitimately returns bytearray for some libraries
        # (e.g., array module buffers) — we should coerce it back to bytes.
        name = "foo.wav"

        def read(self):
            return bytearray(b"\x00" * 1024)

    audio_bytes, filename = validate_clone_inputs(_BytearrayReader(), "demo")
    assert isinstance(audio_bytes, bytes)
    assert audio_bytes == b"\x00" * 1024
    assert filename == "foo.wav"


def test_validate_rejects_text_mode_file_object():
    # StringIO returns str from .read(); reject before it reaches the API as a
    # malformed multipart body or a size check on character count.
    buf = io.StringIO("not binary content")
    buf.name = "foo.wav"
    with pytest.raises(TypeError, match="binary mode"):
        validate_clone_inputs(buf, "demo")


def test_validate_normalizes_windows_path_in_file_object_name():
    class _WindowsPathReader:
        # On Unix, os.sep is "/" so a raw "C:\\..." name would NOT be
        # basename'd; the explicit backslash->slash normalize fixes that.
        name = r"C:\Users\me\voice.wav"

        def read(self):
            return b"\x00" * 1024

    _, filename = validate_clone_inputs(_WindowsPathReader(), "demo")
    assert filename == "voice.wav"


def test_clone_voice_rejects_unknown_model():
    client = Typecast(api_key="test-key")
    with pytest.raises(ValueError, match="model must be one of"):
        client.clone_voice(audio=b"\x00" * 1024, name="demo", model="ssfm-v99")


def test_delete_voice_rejects_non_custom_id():
    client = Typecast(api_key="test-key")
    with pytest.raises(ValueError, match="voice_id must start with"):
        client.delete_voice("tc_not_custom")


def test_delete_voice_rejects_non_string_voice_id():
    client = Typecast(api_key="test-key")
    with pytest.raises(ValueError, match="voice_id must start with"):
        client.delete_voice(12345)  # type: ignore[arg-type]


def test_guess_audio_mime_branches():
    from typecast.client import _guess_audio_mime
    assert _guess_audio_mime("foo.WAV") == "audio/wav"
    assert _guess_audio_mime("foo.MP3") == "audio/mpeg"
    assert _guess_audio_mime("foo.bin") == "application/octet-stream"


def test_clone_voice_propagates_http_error(mocker):
    client = Typecast(api_key="test-key")
    mock_response = mocker.Mock()
    mock_response.status_code = 422
    mock_response.text = '{"error_code": "VALIDATION_ERROR", "message": "bad"}'
    mocker.patch.object(client.session, "post", return_value=mock_response)
    from typecast.exceptions import UnprocessableEntityError
    with pytest.raises(UnprocessableEntityError):
        client.clone_voice(audio=b"\x00" * 1024, name="demo", model="ssfm-v30")


async def test_async_clone_voice_requires_session():
    from typecast.exceptions import TypecastError
    client = AsyncTypecast(host=ASYNC_HOST, api_key="test-key")  # no async with
    with pytest.raises(TypecastError, match="Client session not initialized"):
        await client.clone_voice(audio=b"\x00" * 1024, name="demo", model="ssfm-v30")


async def test_async_delete_voice_requires_session():
    from typecast.exceptions import TypecastError
    client = AsyncTypecast(host=ASYNC_HOST, api_key="test-key")  # no async with
    with pytest.raises(TypecastError, match="Client session not initialized"):
        await client.delete_voice("uc_xxx")


async def test_async_clone_voice_propagates_http_error():
    with aioresponses() as m:
        m.post(
            f"{ASYNC_HOST}/v1/custom-voices/instant-clone",
            status=422,
            payload={"error_code": "VALIDATION_ERROR", "message": "bad"},
        )
        from typecast.exceptions import UnprocessableEntityError
        async with AsyncTypecast(host=ASYNC_HOST, api_key="test-key") as client:
            with pytest.raises(UnprocessableEntityError):
                await client.clone_voice(
                    audio=b"\x00" * 1024, name="demo", model="ssfm-v30"
                )


async def test_async_delete_voice_propagates_http_error():
    with aioresponses() as m:
        m.delete(f"{ASYNC_HOST}/v1/custom-voices/uc_xxx", status=404)
        from typecast.exceptions import NotFoundError as Nfe
        async with AsyncTypecast(host=ASYNC_HOST, api_key="test-key") as client:
            with pytest.raises(Nfe):
                await client.delete_voice("uc_xxx")


async def test_async_custom_voice_workflow_endpoints():
    with aioresponses() as m:
        m.post(
            f"{ASYNC_HOST}/v1/custom-voices/professional-clone",
            status=202,
            payload=CUSTOM_VOICE,
        )
        m.get(f"{ASYNC_HOST}/v1/custom-voices", status=200, payload=[CUSTOM_VOICE])
        m.get(
            f"{ASYNC_HOST}/v1/custom-voices/{CUSTOM_VOICE['voice_id']}",
            status=200,
            payload=CUSTOM_VOICE,
        )
        async with AsyncTypecast(host=ASYNC_HOST, api_key="test-key") as client:
            created = await client.create_professional_voice(
                b"\x00" * 1024, "demo", "en", "ssfm-v30"
            )
            listed = await client.get_custom_voices()
            fetched = await client.get_custom_voice(CUSTOM_VOICE["voice_id"])

    assert created.status == "processing"
    assert listed[0].voice_id == CUSTOM_VOICE["voice_id"]
    assert fetched.source == "professional"


@pytest.mark.parametrize("method,args", [
    ("create_professional_voice", (b"\x00" * 1024, "demo", "en", "ssfm-v30")),
    ("get_custom_voices", ()),
    ("get_custom_voice", (CUSTOM_VOICE["voice_id"],)),
])
async def test_async_custom_voice_endpoints_require_session(method, args):
    from typecast.exceptions import TypecastError

    client = AsyncTypecast(host=ASYNC_HOST, api_key="test-key")
    with pytest.raises(TypecastError, match="Client session not initialized"):
        await getattr(client, method)(*args)


async def test_async_custom_voice_endpoints_propagate_errors():
    with aioresponses() as m:
        m.post(f"{ASYNC_HOST}/v1/custom-voices/professional-clone", status=500)
        m.get(f"{ASYNC_HOST}/v1/custom-voices", status=500)
        m.get(f"{ASYNC_HOST}/v1/custom-voices/{CUSTOM_VOICE['voice_id']}", status=500)
        async with AsyncTypecast(host=ASYNC_HOST, api_key="test-key") as client:
            with pytest.raises(InternalServerError):
                await client.create_professional_voice(b"\x00" * 1024, "demo", "en", "ssfm-v30")
            with pytest.raises(InternalServerError):
                await client.get_custom_voices()
            with pytest.raises(InternalServerError):
                await client.get_custom_voice(CUSTOM_VOICE["voice_id"])


async def test_async_v3_voice_endpoints_cover_current_contract():
    from typecast.exceptions import TypecastError

    client = AsyncTypecast(host=ASYNC_HOST, api_key="test-key")
    with pytest.raises(TypecastError):
        await client.voices_v3()
    with pytest.raises(TypecastError):
        await client.voice_v3("tc_v3")

    with aioresponses() as m:
        m.get(f"{ASYNC_HOST}/v3/voices?model=ssfm-v30", status=200, payload=[V3_VOICE])
        m.get(f"{ASYNC_HOST}/v3/voices", status=500)
        m.get(f"{ASYNC_HOST}/v3/voices/tc_v3", status=200, payload=V3_VOICE)
        m.get(f"{ASYNC_HOST}/v3/voices/tc_missing", status=500)
        async with AsyncTypecast(host=ASYNC_HOST, api_key="test-key") as client:
            voices = await client.voices_v3(VoicesV2Filter(model=TTSModel.SSFM_V30))
            assert voices[0].voice_name.eng == "Voice"
            assert (await client.voice_v3("tc_v3")).voice_id == "tc_v3"
            with pytest.raises(InternalServerError):
                await client.voices_v3()
            with pytest.raises(InternalServerError):
                await client.voice_v3("tc_missing")
