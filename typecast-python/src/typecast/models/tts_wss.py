
from pydantic import BaseModel


class WebSocketMessage(BaseModel):
    type: str
    payload: dict
