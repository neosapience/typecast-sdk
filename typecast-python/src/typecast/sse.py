from typing import AsyncIterator, Optional

import aiohttp

from . import conf
from .exceptions import TypecastError


class TypecastSSE:
    SSE_URL = f"{conf.get_host()}/v1/text-to-speech/sse"

    def __init__(self, api_key: str):
        self.api_key = conf.get_api_key(api_key)
        self.session: Optional[aiohttp.ClientSession] = None

    async def connect(self, endpoint: str) -> AsyncIterator[str]:
        if self.session:
            await self.session.close()

        self.session = aiohttp.ClientSession(
            headers={"X-API-KEY": self.api_key, "Accept": "text/event-stream"}
        )

        async with self.session.get(f"{self.SSE_URL}/{endpoint}") as response:
            if response.status != 200:
                raise TypecastError(f"SSE connection failed: {response.status}")

            async for line in response.content:
                decoded_line = line.decode("utf-8").strip()
                if decoded_line.startswith("data: "):
                    yield decoded_line[6:]

    async def close(self):
        if self.session:
            await self.session.close()
