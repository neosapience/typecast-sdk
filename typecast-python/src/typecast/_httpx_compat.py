from __future__ import annotations

from typing import Any, AsyncIterator, Dict, Iterator, List, Optional, Tuple

import httpx


def _timeout(value: Any) -> Any:
    if isinstance(value, ClientTimeout):
        total = value.total or value.sock_read or 5.0
        return httpx.Timeout(
            total, connect=value.connect or value.sock_connect or total
        )
    if isinstance(value, tuple):
        return httpx.Timeout(value[1], connect=value[0])
    return value


def _headers(defaults: Dict[str, str], supplied: Optional[dict]) -> Dict[str, str]:
    headers = dict(defaults)
    for key, value in (supplied or {}).items():
        if value is None:
            headers.pop(key, None)
        else:
            headers[key] = value
    return headers


class RequestsCompatResponse:
    def __init__(self, response: httpx.Response):
        self._response = response

    def __getattr__(self, name: str) -> Any:
        return getattr(self._response, name)

    def iter_content(self, chunk_size: int) -> Iterator[bytes]:
        return self._response.iter_bytes(chunk_size)

    def close(self) -> None:
        self._response.close()


class RequestsCompatSession:
    def __init__(self, client: Optional[httpx.Client] = None):
        self._client = client or httpx.Client(timeout=None)
        self.headers: Dict[str, str] = {}

    def _request(self, method: str, url: str, **kwargs: Any) -> RequestsCompatResponse:
        stream = kwargs.pop("stream", False)
        kwargs["headers"] = _headers(self.headers, kwargs.get("headers"))
        if "timeout" in kwargs:
            kwargs["timeout"] = _timeout(kwargs["timeout"])
        if stream:
            request = self._client.build_request(method, url, **kwargs)
            response = self._client.send(request, stream=True)
        else:
            response = self._client.request(method, url, **kwargs)
        return RequestsCompatResponse(response)

    def get(self, url: str, **kwargs: Any) -> RequestsCompatResponse:
        return self._request("GET", url, **kwargs)

    def post(self, url: str, **kwargs: Any) -> RequestsCompatResponse:
        return self._request("POST", url, **kwargs)

    def delete(self, url: str, **kwargs: Any) -> RequestsCompatResponse:
        return self._request("DELETE", url, **kwargs)

    def close(self) -> None:
        self._client.close()


class ClientTimeout:
    def __init__(
        self,
        *,
        total: Optional[float] = None,
        connect: Optional[float] = None,
        sock_connect: Optional[float] = None,
        sock_read: Optional[float] = None,
    ):
        self.total = total
        self.connect = connect
        self.sock_connect = sock_connect
        self.sock_read = sock_read


class FormData:
    def __init__(self):
        self.fields: List[Tuple[str, Any, Optional[str], Optional[str]]] = []

    def add_field(
        self,
        name: str,
        value: Any,
        *,
        filename: Optional[str] = None,
        content_type: Optional[str] = None,
    ) -> None:
        self.fields.append((name, value, filename, content_type))


class _AsyncContent:
    def __init__(self, response: httpx.Response):
        self._response = response

    async def iter_chunked(self, chunk_size: int) -> AsyncIterator[bytes]:
        async for chunk in self._response.aiter_bytes(chunk_size):
            yield chunk


class AiohttpCompatResponse:
    def __init__(self, response: httpx.Response):
        self._response = response
        self.status = response.status_code
        self.headers = response.headers
        self.content = _AsyncContent(response)

    async def read(self) -> bytes:
        return await self._response.aread()

    async def text(self) -> str:
        await self._response.aread()
        return self._response.text

    async def json(self) -> Any:
        await self._response.aread()
        return self._response.json()

    async def close(self) -> None:
        await self._response.aclose()


class _AsyncRequestContext:
    def __init__(self, request: Any):
        self._request = request
        self._response: Optional[AiohttpCompatResponse] = None

    async def __aenter__(self) -> AiohttpCompatResponse:
        response = await self._request
        self._response = response
        return response

    async def __aexit__(self, exc_type: Any, exc: Any, tb: Any) -> None:
        if self._response:
            await self._response.close()


class AiohttpCompatSession:
    def __init__(
        self,
        headers: Optional[dict] = None,
        client: Optional[httpx.AsyncClient] = None,
    ):
        self._client = client or httpx.AsyncClient(timeout=300)
        self.headers = dict(headers or {})

    async def _request(
        self, method: str, url: str, **kwargs: Any
    ) -> AiohttpCompatResponse:
        kwargs["headers"] = _headers(self.headers, kwargs.get("headers"))
        if "timeout" in kwargs:
            kwargs["timeout"] = _timeout(kwargs["timeout"])
        form = kwargs.get("data")
        if isinstance(form, FormData):
            kwargs.pop("data")
            kwargs["data"] = {
                name: value
                for name, value, filename, _ in form.fields
                if filename is None
            }
            kwargs["files"] = [
                (name, (filename, value, content_type))
                for name, value, filename, content_type in form.fields
                if filename is not None
            ]
        request = self._client.build_request(method, url, **kwargs)
        response = await self._client.send(request, stream=True)
        return AiohttpCompatResponse(response)

    def get(self, url: str, **kwargs: Any) -> _AsyncRequestContext:
        return _AsyncRequestContext(self._request("GET", url, **kwargs))

    def post(self, url: str, **kwargs: Any) -> _AsyncRequestContext:
        return _AsyncRequestContext(self._request("POST", url, **kwargs))

    def delete(self, url: str, **kwargs: Any) -> _AsyncRequestContext:
        return _AsyncRequestContext(self._request("DELETE", url, **kwargs))

    async def close(self) -> None:
        await self._client.aclose()
