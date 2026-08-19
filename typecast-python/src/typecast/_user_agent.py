import platform
import re
import sys
from importlib import metadata
from typing import Optional

from . import conf


_GENERATED_BY_PATTERN = re.compile(r"[a-z0-9][a-z0-9._-]{0,31}\Z")


def attribution_suffix(
    source: Optional[str] = None,
    generated_by: Optional[str] = None,
) -> str:
    if source is None and generated_by is None:
        return ""
    if (
        not isinstance(source, str)
        or not isinstance(generated_by, str)
        or source not in {"llms", "skill"}
        or not generated_by
    ):
        raise ValueError("source (llms or skill) and generated_by must be provided together")
    if not _GENERATED_BY_PATTERN.fullmatch(generated_by):
        raise ValueError("generated_by must be a lowercase token of at most 32 characters")
    return f" typecast-integration/1 (source={source}; generated_by={generated_by})"


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
    source: Optional[str] = None,
    generated_by: Optional[str] = None,
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
        f"{attribution_suffix(source, generated_by)}"
    )


def requests_user_agent(
    host: str,
    transport: str = "rest",
    source: Optional[str] = None,
    generated_by: Optional[str] = None,
) -> str:
    return build_user_agent(
        mode="sync",
        http_library=f"requests/{_package_version('requests')}",
        host=host,
        transport=transport,
        source=source,
        generated_by=generated_by,
    )


def aiohttp_user_agent(
    host: str,
    transport: str = "rest",
    source: Optional[str] = None,
    generated_by: Optional[str] = None,
) -> str:
    return build_user_agent(
        mode="async",
        http_library=f"aiohttp/{_package_version('aiohttp')}",
        host=host,
        transport=transport,
        source=source,
        generated_by=generated_by,
    )


def httpx_user_agent(
    host: str,
    mode: str,
    transport: str = "rest",
    source: Optional[str] = None,
    generated_by: Optional[str] = None,
) -> str:
    return build_user_agent(
        mode=mode,
        http_library=f"httpx/{_package_version('httpx')}",
        host=host,
        transport=transport,
        source=source,
        generated_by=generated_by,
    )
