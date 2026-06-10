"""
Pytest configuration and fixtures.
"""

import shutil
import socket
import subprocess
import time
import urllib.request
import inspect
import json
from pathlib import Path
from unittest.mock import Mock

import pytest
from aiohttp import ClientResponse, hdrs
from aiohttp.client_reqrep import RequestInfo
from aiohttp.helpers import TimerNoop
from aioresponses.core import RequestMatch, stream_reader_factory
from multidict import CIMultiDict, CIMultiDictProxy
from dotenv import load_dotenv


def pytest_configure(config):
    """Load .env file before running tests."""
    _patch_aioresponses_for_aiohttp_314()

    # Get the project root directory (parent of tests directory)
    project_root = Path(__file__).parent.parent
    env_file = project_root / ".env"

    if env_file.exists():
        load_dotenv(env_file)
        print(f"\n✓ Loaded environment variables from {env_file}")
    else:
        print(f"\n⚠ No .env file found at {env_file}")


def _patch_aioresponses_for_aiohttp_314():
    """Adapt aioresponses 0.7.8 to aiohttp 3.14's ClientResponse signature."""
    if "stream_writer" not in inspect.signature(ClientResponse).parameters:
        return
    if getattr(RequestMatch._build_response, "_typecast_aiohttp_314_patch", False):
        return

    def _build_response(
        self,
        url,
        method=hdrs.METH_GET,
        request_headers=None,
        status=200,
        body="",
        content_type="application/json",
        payload=None,
        headers=None,
        response_class=None,
        reason=None,
    ):
        """Build a mocked ClientResponse using aiohttp 3.14 constructor args."""
        if response_class is None:
            response_class = ClientResponse
        if payload is not None:
            body = json.dumps(payload)
        if not isinstance(body, bytes):
            body = str.encode(body)
        if request_headers is None:
            request_headers = {}

        loop = Mock()
        loop.get_debug = Mock(return_value=True)
        kwargs = {
            "request_info": RequestInfo(
                url=url,
                method=method,
                headers=CIMultiDictProxy(CIMultiDict(**request_headers)),
                real_url=url,
            ),
            "writer": None,
            "continue100": None,
            "timer": TimerNoop(),
            "traces": [],
            "loop": loop,
            "session": None,
            "stream_writer": Mock(),
        }

        response_headers = CIMultiDict({hdrs.CONTENT_TYPE: content_type})
        if headers:
            response_headers.update(headers)
        raw_headers = self._build_raw_headers(response_headers)
        resp = response_class(method, url, **kwargs)

        for header in response_headers.getall(hdrs.SET_COOKIE, ()):
            resp.cookies.load(header)

        resp._headers = response_headers
        resp._raw_headers = raw_headers
        resp.status = status
        resp.reason = reason
        resp.content = stream_reader_factory(loop)
        resp.content.feed_data(body)
        resp.content.feed_eof()
        return resp

    _build_response._typecast_aiohttp_314_patch = True
    RequestMatch._build_response = _build_response


def _free_port() -> int:
    """Find a free TCP port."""
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.bind(("127.0.0.1", 0))
    port = s.getsockname()[1]
    s.close()
    return port


@pytest.fixture(scope="session")
def mock_server():
    """Start the shared mock server (test-fixtures/mock-server) for the
    duration of a test session and yield its base URL.

    Skips cleanly if the mock server can't be spawned (e.g. Node.js not
    installed on the runner). The dedicated coverage-python CI workflow
    installs Node before running tests so this fixture is exercised
    end-to-end there; the legacy CI doesn't install Node so the SSE/WS
    tests skip there but still pass elsewhere."""
    repo_root = Path(__file__).resolve().parent.parent.parent
    mock_dir = repo_root / "test-fixtures" / "mock-server"
    if not mock_dir.exists():
        pytest.skip("mock-server directory not found")

    if shutil.which("npx") is None or shutil.which("npm") is None:
        pytest.skip("Node.js (npx/npm) not available — skipping mock-server tests")

    if not (mock_dir / "node_modules").exists():
        try:
            subprocess.run(
                ["npm", "ci"], cwd=str(mock_dir), check=True, capture_output=True
            )
        except (subprocess.CalledProcessError, FileNotFoundError) as e:
            pytest.skip(f"failed to install mock-server deps: {e}")

    port = _free_port()
    try:
        proc = subprocess.Popen(
            ["npx", "tsx", "src/index.ts", "--port", str(port)],
            cwd=str(mock_dir),
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
    except FileNotFoundError as e:
        pytest.skip(f"failed to spawn mock server: {e}")

    base_url = f"http://127.0.0.1:{port}"
    deadline = time.time() + 30
    last_err = None
    while time.time() < deadline:
        if proc.poll() is not None:
            pytest.skip(f"mock server process exited prematurely with code {proc.returncode}")
        try:
            with urllib.request.urlopen(f"{base_url}/__mock_health", timeout=1) as r:
                if r.status == 200:
                    break
        except Exception as e:
            last_err = e
            time.sleep(0.2)
    else:
        proc.terminate()
        pytest.skip(f"mock server did not become ready: {last_err}")

    yield base_url

    proc.terminate()
    try:
        proc.wait(timeout=5)
    except subprocess.TimeoutExpired:
        proc.kill()
