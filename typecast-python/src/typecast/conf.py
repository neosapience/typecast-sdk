import os

TYPECAST_API_HOST = "https://api.typecast.ai"


def get_host(host=None):
    if host:  # Parameter takes priority
        return host
    env_host = os.getenv("TYPECAST_API_HOST")  # Check environment variable
    return env_host if env_host else TYPECAST_API_HOST  # Use default if not set


def get_api_key(api_key=None):
    if api_key:  # Parameter takes priority
        return api_key
    return os.getenv("TYPECAST_API_KEY")  # Return from environment variable
