import os

TYPECAST_API_HOST = "https://api.typecast.ai"


def get_host(host=None):
    if host:  # Parameter takes priority
        return normalize_host(host)
    env_host = os.getenv("TYPECAST_API_HOST")  # Check environment variable
    return normalize_host(env_host) if env_host else TYPECAST_API_HOST  # Use default if not set


def get_api_key(api_key=None):
    if api_key:  # Parameter takes priority
        return api_key.strip()
    env_key = os.getenv("TYPECAST_API_KEY")  # Return from environment variable
    return env_key.strip() if env_key else None


def normalize_host(host):
    return host.strip().rstrip("/")


def is_default_host(host):
    return normalize_host(host).lower() == TYPECAST_API_HOST
