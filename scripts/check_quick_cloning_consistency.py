#!/usr/bin/env python3
"""Cross-SDK consistency check for POST /v1/voices/clone multipart bodies.

Phase 1: Python + JS only. Phase 2 PR will extend to all 11 SDKs by adding
small drivers per language and updating this script's invocation list.

Compares structural fields, not byte-equality:
- name field value
- model field value
- file part: filename, byte length, sha256

Exits 0 on match, 1 on any mismatch.
"""
from __future__ import annotations

import hashlib
import http.server
import re
import socketserver
import subprocess
import sys
import threading
from pathlib import Path
from typing import Optional


ROOT = Path(__file__).resolve().parents[1]
SAMPLE = ROOT / "test-fixtures" / "quick-cloning" / "sample.wav"
RESPONSE_BODY = (
    b'{"voice_id": "uc_consistency_check", "name": "consistency-check", "model": "ssfm-v30"}'
)


class _StubHandler(http.server.BaseHTTPRequestHandler):
    captured_body: Optional[bytes] = None
    captured_content_type: Optional[str] = None

    def do_POST(self) -> None:
        length = int(self.headers["Content-Length"])
        _StubHandler.captured_body = self.rfile.read(length)
        _StubHandler.captured_content_type = self.headers.get("Content-Type", "")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(RESPONSE_BODY)))
        self.end_headers()
        self.wfile.write(RESPONSE_BODY)

    def log_message(self, format: str, *args) -> None:  # noqa: A002 - silence logs
        pass


def _start_stub() -> tuple[str, socketserver.TCPServer]:
    server = socketserver.TCPServer(("127.0.0.1", 0), _StubHandler)
    port = server.server_address[1]
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return f"http://127.0.0.1:{port}", server


def _parse_multipart(body: bytes, content_type: str) -> dict:
    """Parse multipart body into {name, model, file: {filename, length, sha256, mime}}.

    Uses raw boundary splitting rather than email.parser to avoid edge cases
    with bare multipart (no top-level From/MIME-Version headers).
    """
    m = re.search(r"boundary=([^;\s]+)", content_type)
    if not m:
        raise RuntimeError(f"no boundary in content-type: {content_type!r}")
    boundary = m.group(1).strip('"')
    delimiter = b"--" + boundary.encode()
    parts = body.split(delimiter)
    fields: dict = {}
    for part in parts:
        part = part.strip(b"\r\n-")
        if not part:
            continue
        if b"\r\n\r\n" not in part:
            continue
        header_blob, content = part.split(b"\r\n\r\n", 1)
        content = content.rstrip(b"\r\n")
        headers: dict[str, str] = {}
        for line in header_blob.split(b"\r\n"):
            if b":" in line:
                k, v = line.split(b":", 1)
                headers[k.strip().decode().lower()] = v.strip().decode()
        disposition = headers.get("content-disposition", "")
        name_m = re.search(r'name="([^"]+)"', disposition)
        if not name_m:
            continue
        field_name = name_m.group(1)
        filename_m = re.search(r'filename="([^"]+)"', disposition)
        if filename_m:
            fields[field_name] = {
                "filename": filename_m.group(1),
                "length": len(content),
                "sha256": hashlib.sha256(content).hexdigest(),
                "mime": headers.get("content-type", ""),
            }
        else:
            fields[field_name] = content.decode()
    return fields


def _run_python(host: str) -> tuple[bytes, str]:
    """Invoke Python SDK clone_voice against the stub server."""
    sample_str = str(SAMPLE)
    code = (
        "import os; "
        "os.environ['TYPECAST_API_KEY'] = 'consistency-stub'; "
        f"os.environ['TYPECAST_API_HOST'] = '{host}'; "
        "from typecast import Typecast; "
        "c = Typecast(); "
        f"c.clone_voice(audio='{sample_str}', name='consistency-check', model='ssfm-v30')"
    )
    result = subprocess.run(
        ["uv", "run", "--project", "typecast-python", "python", "-c", code],
        cwd=ROOT,
        capture_output=True,
    )
    if result.returncode != 0:
        print("Python SDK stderr:", result.stderr.decode(), file=sys.stderr)
        raise RuntimeError(f"Python SDK invocation failed (exit {result.returncode})")
    body = _StubHandler.captured_body
    ct = _StubHandler.captured_content_type
    _StubHandler.captured_body = None
    _StubHandler.captured_content_type = None
    assert body is not None, "Python stub did not receive a POST"
    return body, ct or ""


def _run_js(host: str) -> tuple[bytes, str]:
    """Invoke JS SDK cloneVoice against the stub server."""
    sample_str = str(SAMPLE)
    # Use CJS require so we don't need ESM loader setup.
    code = f"""
const {{ TypecastClient }} = require({repr(str(ROOT / 'typecast-js' / 'lib' / 'index.cjs'))});
const c = new TypecastClient({{ apiKey: 'consistency-stub', baseHost: {repr(host)} }});
c.cloneVoice({{ audio: {repr(sample_str)}, name: 'consistency-check', model: 'ssfm-v30' }})
  .then(() => process.exit(0))
  .catch(e => {{ console.error(String(e)); process.exit(1); }});
"""
    result = subprocess.run(
        ["node", "-e", code],
        cwd=ROOT,
        capture_output=True,
    )
    if result.returncode != 0:
        print("JS SDK stderr:", result.stderr.decode(), file=sys.stderr)
        raise RuntimeError(f"JS SDK invocation failed (exit {result.returncode})")
    body = _StubHandler.captured_body
    ct = _StubHandler.captured_content_type
    _StubHandler.captured_body = None
    _StubHandler.captured_content_type = None
    assert body is not None, "JS stub did not receive a POST"
    return body, ct or ""


def main() -> int:
    if not SAMPLE.exists():
        print(f"ERROR: missing fixture: {SAMPLE}", file=sys.stderr)
        return 1

    js_lib = ROOT / "typecast-js" / "lib" / "index.cjs"
    if not js_lib.exists():
        print(
            "ERROR: typecast-js/lib not built. Run: cd typecast-js && npm run build",
            file=sys.stderr,
        )
        return 1

    host, server = _start_stub()
    try:
        print(f"Stub server listening at {host}")
        print("Running Python SDK clone_voice …")
        py_body, py_ct = _run_python(host)

        print("Running JS SDK cloneVoice …")
        js_body, js_ct = _run_js(host)
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    finally:
        server.shutdown()

    py_fields = _parse_multipart(py_body, py_ct)
    js_fields = _parse_multipart(js_body, js_ct)

    # --- Print side-by-side summary ---
    print()
    print("Field            Python                                   JS")
    print("-" * 80)
    for key in ("name", "model"):
        pv = py_fields.get(key, "<missing>")
        jv = js_fields.get(key, "<missing>")
        print(f"{key:<16} {pv!r:<40} {jv!r}")
    py_file = py_fields.get("file", {})
    js_file = js_fields.get("file", {})
    for sub in ("filename", "length", "sha256", "mime"):
        pv = py_file.get(sub, "<missing>") if isinstance(py_file, dict) else "<missing>"
        jv = js_file.get(sub, "<missing>") if isinstance(js_file, dict) else "<missing>"
        label = f"file.{sub}"
        print(f"{label:<16} {str(pv)!r:<40} {str(jv)!r}")
    print()

    # --- Assert structural equivalence ---
    issues: list[str] = []
    for key in ("name", "model"):
        if py_fields.get(key) != js_fields.get(key):
            issues.append(
                f"{key}: python={py_fields.get(key)!r} js={js_fields.get(key)!r}"
            )
    for sub in ("length", "sha256", "mime"):
        pv = py_file.get(sub) if isinstance(py_file, dict) else None
        jv = js_file.get(sub) if isinstance(js_file, dict) else None
        if pv != jv:
            issues.append(f"file.{sub}: python={pv!r} js={jv!r}")

    if issues:
        print("MISMATCH:", file=sys.stderr)
        for issue in issues:
            print(f"  - {issue}", file=sys.stderr)
        return 1

    file_len = py_file.get("length", "?") if isinstance(py_file, dict) else "?"
    print(
        f"OK: Python and JS produce structurally identical multipart bodies"
        f" (file length {file_len} bytes)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
