"""
Pytest configuration and fixtures.
"""

from pathlib import Path

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
