"""Tests for typecast.conf module."""

import os

import pytest

from typecast import conf


class TestGetHost:
    def test_explicit_host_wins(self, monkeypatch):
        monkeypatch.setenv("TYPECAST_API_HOST", "https://from-env.example")
        assert conf.get_host("https://explicit.example") == "https://explicit.example"

    def test_env_host_used_when_no_arg(self, monkeypatch):
        monkeypatch.setenv("TYPECAST_API_HOST", "https://from-env.example")
        assert conf.get_host() == "https://from-env.example"

    def test_default_host_when_no_arg_no_env(self, monkeypatch):
        monkeypatch.delenv("TYPECAST_API_HOST", raising=False)
        assert conf.get_host() == "https://api.typecast.ai"

    def test_default_host_when_env_is_empty_string(self, monkeypatch):
        monkeypatch.setenv("TYPECAST_API_HOST", "")
        assert conf.get_host() == "https://api.typecast.ai"


class TestGetApiKey:
    def test_explicit_key_wins(self, monkeypatch):
        monkeypatch.setenv("TYPECAST_API_KEY", "env-key")
        assert conf.get_api_key("explicit-key") == "explicit-key"

    def test_env_key_used_when_no_arg(self, monkeypatch):
        monkeypatch.setenv("TYPECAST_API_KEY", "env-key")
        assert conf.get_api_key() == "env-key"

    def test_returns_none_when_neither_set(self, monkeypatch):
        monkeypatch.delenv("TYPECAST_API_KEY", raising=False)
        assert conf.get_api_key() is None
