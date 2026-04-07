"""Tests for TypecastSSE using the shared mock server."""

import pytest

from typecast.sse import TypecastSSE
from typecast.exceptions import TypecastError


class TestTypecastSSE:
    async def test_connect_streams_data_lines(self, mock_server):
        """Connect to a known SSE script and collect emitted data."""
        client = TypecastSSE(api_key="test", sse_url=f"{mock_server}/__mock_sse")
        chunks: list[str] = []
        try:
            async for chunk in client.connect("ssfm-stream-1"):
                chunks.append(chunk)
        finally:
            await client.close()

        # The mock server's ssfm-stream-1.txt fixture emits 4 SSE events
        # (3 progress + 1 done), each with `data: <json>`. The SDK strips
        # the `data: ` prefix and yields the payload.
        assert len(chunks) == 4
        assert any("progress" in c for c in chunks)
        assert "[DONE]" in chunks[-1]

    async def test_connect_failure_raises(self, mock_server):
        """Hitting an unknown SSE script returns 404 → TypecastError."""
        client = TypecastSSE(api_key="test", sse_url=f"{mock_server}/__mock_sse")
        try:
            with pytest.raises(TypecastError, match="SSE connection failed"):
                async for _ in client.connect("nonexistent-script"):
                    pass
        finally:
            await client.close()

    async def test_close_without_connect_is_noop(self):
        """Calling close() without ever calling connect() does nothing."""
        client = TypecastSSE(api_key="test")
        await client.close()  # should not raise

    async def test_reconnect_closes_previous_session(self, mock_server):
        """Calling connect() twice closes the previous session before opening a new one."""
        client = TypecastSSE(api_key="test", sse_url=f"{mock_server}/__mock_sse")
        try:
            async for _ in client.connect("ssfm-stream-1"):
                pass
            session_1 = client.session
            assert session_1 is not None

            async for _ in client.connect("ssfm-stream-1"):
                pass
            assert client.session is not session_1
        finally:
            await client.close()

    def test_default_sse_url_uses_host(self):
        """Without sse_url override, sse_url is derived from host."""
        client = TypecastSSE(api_key="test", host="https://custom.example")
        assert client.sse_url == "https://custom.example/v1/text-to-speech/sse"

    def test_sse_url_override_takes_precedence(self):
        """When sse_url is provided, it overrides the host-derived URL."""
        client = TypecastSSE(
            api_key="test",
            host="https://custom.example",
            sse_url="https://override.example/sse",
        )
        assert client.sse_url == "https://override.example/sse"
