import asyncio

import httpx
import pytest

import typecast.async_client as async_client_module
import typecast.client as client_module
from typecast._httpx_compat import (
    AiohttpCompatSession,
    ClientTimeout,
    FormData,
    RequestsCompatSession,
    _AsyncRequestContext,
    _headers,
    _timeout,
)
from typecast._user_agent import httpx_user_agent
from typecast.async_client import AsyncTypecast
from typecast.client import Typecast


def _handler(request: httpx.Request) -> httpx.Response:
    if request.url.path == "/json":
        return httpx.Response(200, json={"ok": True})
    return httpx.Response(204 if request.method == "DELETE" else 200, content=b"abcdef")


def test_timeout_and_header_conversion():
    timeout = _timeout(ClientTimeout(total=60, connect=10))
    assert timeout.read == 60
    assert timeout.connect == 10

    stream_timeout = _timeout(ClientTimeout(sock_connect=5, sock_read=30))
    assert stream_timeout.read == 30
    assert stream_timeout.connect == 5

    tuple_timeout = _timeout((2, 8))
    assert tuple_timeout.read == 8
    assert tuple_timeout.connect == 2
    assert _timeout(None) is None
    assert _headers({"Content-Type": "application/json"}, None) == {
        "Content-Type": "application/json"
    }
    assert _headers(
        {"Content-Type": "application/json"},
        {"Content-Type": None, "X-Test": "yes"},
    ) == {"X-Test": "yes"}


def test_requests_compat_session():
    client = httpx.Client(transport=httpx.MockTransport(_handler))
    session = RequestsCompatSession(client)
    session.headers.update({"Content-Type": "application/json", "X-Test": "yes"})

    response = session.post("https://example.test/json", json={"hello": "world"})
    assert response.status_code == 200
    assert response.json() == {"ok": True}

    stream = session.post("https://example.test/stream", stream=True, timeout=(2, 8))
    assert b"".join(stream.iter_content(2)) == b"abcdef"
    stream.close()
    assert session.get("https://example.test/data").content == b"abcdef"
    assert session.delete("https://example.test/data").status_code == 204
    session.close()


def test_sync_client_selects_httpx_fallback(monkeypatch):
    monkeypatch.setattr(client_module, "requests", None)
    monkeypatch.setattr(
        client_module, "RequestsCompatSession", RequestsCompatSession, raising=False
    )
    client = Typecast(host="https://example.test", api_key="key")
    assert isinstance(client.session, RequestsCompatSession)
    assert "httpx/" in client.session.headers["User-Agent"]
    assert "mode=sync" in httpx_user_agent(client.host, "sync")
    client.session.close()


@pytest.mark.asyncio
async def test_aiohttp_compat_session():
    client = httpx.AsyncClient(transport=httpx.MockTransport(_handler))
    session = AiohttpCompatSession(headers={"X-Test": "yes"}, client=client)

    async with session.get("https://example.test/json") as response:
        assert response.status == 200
        assert await response.json() == {"ok": True}

    async with session.get("https://example.test/data") as response:
        assert await response.read() == b"abcdef"

    async with session.get("https://example.test/data") as response:
        assert await response.text() == "abcdef"

    form = FormData()
    form.add_field("name", "voice")
    form.add_field("file", b"audio", filename="voice.wav", content_type="audio/wav")
    async with session.post(
        "https://example.test/data",
        data=form,
        timeout=ClientTimeout(total=60, connect=10),
    ) as response:
        chunks = [chunk async for chunk in response.content.iter_chunked(2)]
        assert b"".join(chunks) == b"abcdef"

    async with session.delete("https://example.test/data") as response:
        assert response.status == 204

    future = asyncio.get_running_loop().create_future()
    future.set_result(None)
    await _AsyncRequestContext(future).__aexit__(None, None, None)
    await session.close()


@pytest.mark.asyncio
async def test_async_client_selects_httpx_fallback(monkeypatch):
    monkeypatch.setattr(async_client_module, "aiohttp", None)
    monkeypatch.setattr(
        async_client_module,
        "AiohttpCompatSession",
        AiohttpCompatSession,
        raising=False,
    )
    async with AsyncTypecast(host="https://example.test", api_key="key") as client:
        assert isinstance(client.session, AiohttpCompatSession)
        assert "httpx/" in client.session.headers["User-Agent"]
