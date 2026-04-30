#!/usr/bin/env python3
"""Cross-SDK consistency check for the with-timestamps wrapping.

Runs Python's TTSWithTimestampsResponse and JS's WithTimestampsResult against
the same fixture inputs and asserts the SRT/VTT outputs match the expected
files byte-for-byte. Add new SDKs to ALL_SDKS as they ship.
"""
from __future__ import annotations

import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
FIX = ROOT / "test-fixtures" / "with-timestamps"

ALL_SDKS = ("python", "js")  # 후속 PR 에서 9개 추가

FIXTURES = ("both", "word_only", "char_only", "jpn_char")


def run_python(name: str, fmt: str) -> str:
    code = f"""
import json, sys
sys.path.insert(0, "{ROOT / 'typecast-python' / 'src'}")
from typecast.models.tts import TTSWithTimestampsResponse
data = json.loads(open("{FIX / f'{name}.json'}").read())
resp = TTSWithTimestampsResponse.model_validate(data)
sys.stdout.write(resp.to_{fmt}())
"""
    return subprocess.check_output([sys.executable, "-c", code], text=True)


def run_js(name: str, fmt: str) -> str:
    # The JS build outputs to lib/ (not dist/) and provides both ESM (lib/index.js)
    # and CJS (lib/index.cjs). We use CJS via require() for simplicity.
    dist_path = ROOT / "typecast-js" / "lib" / "index.cjs"
    method = f"to{fmt.title()}"  # toSrt / toVtt
    script = f"""
const {{ WithTimestampsResult }} = require("{dist_path}");
const fs = require("fs");
const data = JSON.parse(fs.readFileSync("{FIX / f'{name}.json'}", "utf-8"));
const r = new WithTimestampsResult(data);
process.stdout.write(r.{method}());
"""
    return subprocess.check_output(["node", "-e", script], text=True)


RUNNERS = {"python": run_python, "js": run_js}


def main() -> int:
    failures = []
    for name in FIXTURES:
        for fmt in ("srt", "vtt"):
            expected_path = FIX / "expected" / f"{name}.{fmt}"
            expected = expected_path.read_text(encoding="utf-8")
            for sdk in ALL_SDKS:
                actual = RUNNERS[sdk](name, fmt)
                if actual != expected:
                    failures.append(f"{sdk}/{name}.{fmt}: byte mismatch")
                    # Print a short diff for debugging
                    exp_lines = expected.splitlines()
                    act_lines = actual.splitlines()
                    print(f"\n--- expected ({name}.{fmt}) ---", file=sys.stderr)
                    for i, (e, a) in enumerate(zip(exp_lines, act_lines)):
                        if e != a:
                            print(f"  line {i+1}: expected={e!r}", file=sys.stderr)
                            print(f"  line {i+1}:   actual={a!r}", file=sys.stderr)
                    if len(exp_lines) != len(act_lines):
                        print(
                            f"  line count: expected={len(exp_lines)}, actual={len(act_lines)}",
                            file=sys.stderr,
                        )
    if failures:
        print("FAIL:", *failures, sep="\n  ")
        return 1
    print(
        f"OK: {len(ALL_SDKS)} SDKs × {len(FIXTURES)} fixtures × 2 formats matched."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
