"""Tests for TypecastWebSocket using the shared mock server."""

import asyncio

import pytest

from typecast.exceptions import TypecastError
from typecast.models import WebSocketMessage
from typecast.websocket import TypecastWebSocket


def _ws_url(mock_server: str, name: str) -> str:
    return mock_server.replace("http://", "ws://") + f"/__mock_ws/{name}"


class TestWebSocketDefaults:
    def test_default_ws_url(self):
        client = TypecastWebSocket(api_key="test")
        assert client.ws_url == TypecastWebSocket.DEFAULT_WS_URL

    def test_custom_ws_url_override(self):
        client = TypecastWebSocket(api_key="test", ws_url="ws://custom.example/ws")
        assert client.ws_url == "ws://custom.example/ws"


class TestWebSocketConnect:
    async def test_connect_starts_session(self, mock_server):
        client = TypecastWebSocket(
            api_key="test", ws_url=_ws_url(mock_server, "text-only")
        )
        await client.connect()
        try:
            assert client.ws is not None
            await asyncio.sleep(0.2)
        finally:
            await client.close()

    async def test_send_without_connect_raises(self):
        client = TypecastWebSocket(api_key="test", ws_url="ws://nowhere.invalid/none")
        with pytest.raises(TypecastError, match="not connected"):
            await client.send(WebSocketMessage(type="test", payload={}))

    async def test_close_without_connect_is_noop(self):
        client = TypecastWebSocket(api_key="test")
        await client.close()


class TestWebSocketCallbacks:
    async def test_on_registers_callback_and_handler_invokes_it(self, mock_server):
        received: list = []

        async def cb(payload):
            received.append(payload)

        client = TypecastWebSocket(
            api_key="test", ws_url=_ws_url(mock_server, "text-only")
        )
        client.on("start", cb)
        client.on("end", cb)
        client.on("progress", cb)

        await client.connect()
        try:
            # text-only fixture emits 3 text frames + close in ~40ms
            await asyncio.sleep(1.0)
        finally:
            await client.close()

        # All three callbacks should have fired
        assert len(received) == 3


class TestWebSocketSend:
    async def test_send_after_connect(self, mock_server):
        client = TypecastWebSocket(
            api_key="test", ws_url=_ws_url(mock_server, "text-only")
        )
        await client.connect()
        try:
            await client.send(WebSocketMessage(type="test", payload={"hello": "world"}))
        finally:
            await client.close()


class TestMessageHandlerEdgeCases:
    async def test_handler_returns_when_ws_is_none(self):
        """The early-return guard at the top of _message_handler."""
        client = TypecastWebSocket(api_key="test")
        await client._message_handler()  # should not raise
