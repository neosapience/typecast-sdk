from typing import AsyncIterator, Optional

import aiohttp

from . import conf
from .exceptions import TypecastError


class TypecastSSE:
    """Server-Sent Events client for Typecast streaming endpoints."""

    def __init__(
        self,
        api_key: Optional[str] = None,
        host: Optional[str] = None,
        sse_url: Optional[str] = None,
    ):
        """Initialize the SSE client.

        Args:
            api_key: API key. Defaults to TYPECAST_API_KEY env var.
            host: API host. Defaults to TYPECAST_API_HOST env var or
                'https://api.typecast.ai'. Used to derive sse_url when
                sse_url is not provided.
            sse_url: Full SSE base URL override (escape hatch for tests).
                When provided, takes precedence over host.
        """
        self.api_key = conf.get_api_key(api_key)
        self.host = conf.get_host(host)
        self._sse_url_override = sse_url
        self.session: Optional[aiohttp.ClientSession] = None

    @property
    def sse_url(self) -> str:
        if self._sse_url_override is not None:
            return self._sse_url_override
        return f"{self.host}/v1/text-to-speech/sse"

    async def connect(self, endpoint: str) -> AsyncIterator[str]:
        if self.session:
            await self.session.close()

        self.session = aiohttp.ClientSession(
            headers={"X-API-KEY": self.api_key, "Accept": "text/event-stream"}
        )

        async with self.session.get(f"{self.sse_url}/{endpoint}") as response:
            if response.status != 200:
                raise TypecastError(f"SSE connection failed: {response.status}")

            async for line in response.content:
                decoded_line = line.decode("utf-8").strip()
                if decoded_line.startswith("data: "):
                    yield decoded_line[6:]

    async def close(self):
        if self.session:
            await self.session.close()
