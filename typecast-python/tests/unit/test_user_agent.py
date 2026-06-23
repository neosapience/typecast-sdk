from importlib import metadata

from typecast import _user_agent


def test_user_agent_uses_dev_version_when_package_metadata_is_missing(monkeypatch):
    def missing_version(_package_name):
        raise metadata.PackageNotFoundError

    monkeypatch.setattr(_user_agent.metadata, "version", missing_version)

    user_agent = _user_agent.requests_user_agent("https://proxy.example")

    assert user_agent.startswith("typecast-python/dev ")
