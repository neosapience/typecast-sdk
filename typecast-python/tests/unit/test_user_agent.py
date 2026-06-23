from importlib import metadata

from typecast import _user_agent


def test_user_agent_uses_dev_version_when_package_metadata_is_missing(monkeypatch):
    def missing_version(_package_name):
        raise metadata.PackageNotFoundError

    monkeypatch.setattr(_user_agent.metadata, "version", missing_version)

    user_agent = _user_agent.requests_user_agent("https://proxy.example")

    assert user_agent.startswith("typecast-python/dev ")


def test_user_agent_normalizes_os_and_arch(monkeypatch):
    monkeypatch.setattr(_user_agent.platform, "system", lambda: "Darwin")
    monkeypatch.setattr(_user_agent.platform, "machine", lambda: "x86_64")

    user_agent = _user_agent.requests_user_agent("https://proxy.example")

    assert "os=macos" in user_agent
    assert "arch=x64" in user_agent


def test_user_agent_normalizes_windows_and_arm64(monkeypatch):
    monkeypatch.setattr(_user_agent.platform, "system", lambda: "Windows")
    monkeypatch.setattr(_user_agent.platform, "machine", lambda: "aarch64")

    user_agent = _user_agent.requests_user_agent("https://proxy.example")

    assert "os=windows" in user_agent
    assert "arch=arm64" in user_agent


def test_user_agent_normalizes_unknown_platform_context(monkeypatch):
    monkeypatch.setattr(_user_agent.platform, "system", lambda: "")
    monkeypatch.setattr(_user_agent.platform, "machine", lambda: "")

    user_agent = _user_agent.requests_user_agent("https://proxy.example")

    assert "os=unknown" in user_agent
    assert "arch=unknown" in user_agent
