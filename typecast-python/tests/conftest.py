"""
Pytest configuration and fixtures.
"""

import os
import shutil
import socket
import subprocess
import time
import urllib.request
from pathlib import Path

import pytest
from dotenv import load_dotenv


def pytest_configure(config):
    """Load .env file before running tests."""
    # Get the project root directory (parent of tests directory)
    project_root = Path(__file__).parent.parent
    env_file = project_root / ".env"

    if env_file.exists():
        load_dotenv(env_file)
        print(f"\n✓ Loaded environment variables from {env_file}")
    else:
        print(f"\n⚠ No .env file found at {env_file}")


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
