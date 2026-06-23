import platform
import sys
from importlib import metadata

import aiohttp
import requests

from . import conf


def _package_version(package_name: str) -> str:
    try:
        return metadata.version(package_name)
    except metadata.PackageNotFoundError:
        return "dev"


def _os_name() -> str:
    system = platform.system().lower()
    if system == "darwin":
        return "macos"
    if system.startswith("windows"):
        return "windows"
    return system or "unknown"


def _arch_name() -> str:
    machine = platform.machine().lower()
    if machine in {"x86_64", "amd64"}:
        return "x64"
    if machine in {"aarch64", "arm64"}:
        return "arm64"
    return machine or "unknown"


def build_user_agent(
    *,
    mode: str,
    http_library: str,
    host: str,
    transport: str = "rest",
) -> str:
    sdk_version = _package_version("typecast-python")
    base = "default" if conf.is_default_host(host) else "custom"
    return (
        f"typecast-python/{sdk_version} "
        f"Python/{sys.version_info.major}.{sys.version_info.minor} "
        f"{platform.python_implementation()}/{platform.python_version()} "
        f"{http_library} "
        f"(mode={mode}; base={base}; transport={transport}; "
        f"os={_os_name()}; arch={_arch_name()}; sdk_env=python; platform=server)"
    )


def requests_user_agent(host: str, transport: str = "rest") -> str:
    return build_user_agent(
        mode="sync",
        http_library=f"requests/{requests.__version__}",
        host=host,
        transport=transport,
    )


def aiohttp_user_agent(host: str, transport: str = "rest") -> str:
    return build_user_agent(
        mode="async",
        http_library=f"aiohttp/{aiohttp.__version__}",
        host=host,
        transport=transport,
    )
