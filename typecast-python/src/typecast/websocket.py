import asyncio
import json
from typing import Callable, Optional

import websockets

from .exceptions import TypecastError
from .models import WebSocketMessage


class TypecastWebSocket:
    WS_URL = "wss://api.typecast.ai/v1/ws"

    def __init__(self, api_key: str):
        self.api_key = api_key
        self.ws: Optional[websockets.WebSocketClientProtocol] = None
        self.callbacks: dict[str, Callable] = {}

    async def connect(self):
        self.ws = await websockets.connect(f"{self.WS_URL}?token={self.api_key}")

        # Start message handler
        asyncio.create_task(self._message_handler())

    async def _message_handler(self):
        if not self.ws:
            return

        async for message in self.ws:
            data = json.loads(message)
            msg = WebSocketMessage(**data)

            if msg.type in self.callbacks:
                await self.callbacks[msg.type](msg.payload)

    def on(self, event_type: str, callback: Callable):
        """Register event callback"""
        self.callbacks[event_type] = callback

    async def send(self, message: WebSocketMessage):
        if not self.ws:
            raise TypecastError("WebSocket not connected")
        await self.ws.send(message.model_dump_json())

    async def close(self):
        if self.ws:
            await self.ws.close()
