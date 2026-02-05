from typing import Optional

from pydantic import BaseModel


class Error(BaseModel):
    code: str
    message: str
    details: Optional[dict] = None
