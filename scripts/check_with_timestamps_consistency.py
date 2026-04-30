#!/usr/bin/env python3
"""Cross-SDK consistency check for the with-timestamps wrapping.

Runs Python's TTSWithTimestampsResponse and JS's WithTimestampsResult against
the same fixture inputs and asserts the SRT/VTT outputs match the expected
files byte-for-byte. Add new SDKs to ALL_SDKS as they ship.
"""
from __future__ import annotations

import json
import os
import pathlib
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
FIX = ROOT / "test-fixtures" / "with-timestamps"

ALL_SDKS = ("python", "js", "go", "java")
# NOTE: Rust is intentionally excluded from ALL_SDKS here.
# The Rust SDK byte-equality consistency is verified directly in its #[test] suite
# (tests/timestamps_test.rs), which deserializes each shared fixture and asserts
# to_srt()/to_vtt() matches the expected/* files exactly via assert_eq! on the
# String contents.  Driving the Rust SDK from this Python script would require
# either (a) building a standalone Rust binary via `cargo build --example` and
# parsing its stdout, or (b) a `cargo run` invocation with a temporary Cargo
# project — both add fragile shell orchestration (Cargo workspace resolver edge
# cases, cross-platform binary paths) for no additional coverage signal beyond
# what the #[test] suite already provides.
#
# NOTE: Kotlin is intentionally excluded from ALL_SDKS here.
# The Kotlin SDK byte-equality consistency is verified directly in its JUnit test suite
# (TimestampTTSTest.kt), which decodes each shared fixture and asserts toSrt()/toVtt()
# matches the expected/* files exactly.  Driving the Kotlin SDK from this Python script
# would require either (a) a `kotlinc -script` runner that must locate the Gradle-built
# jar + all kotlinx.serialization runtime jars on the classpath, or (b) a Gradle task
# that writes to stdout — both add fragile shell orchestration for no additional coverage
# signal beyond what the JUnit tests already provide.
#
# NOTE: C# is intentionally excluded from ALL_SDKS here.
# The C# SDK byte-equality consistency is verified directly in its xUnit test suite
# (TimestampTTSTests.cs), which deserializes each shared fixture and asserts ToSrt()/ToVtt()
# matches the expected/* files exactly (Assert.Equal on string content).
# Driving the C# SDK from this Python script would require either (a) a `dotnet script`
# runner with special NuGet setup, or (b) a standalone console project under scripts/ plus
# `dotnet run` invocation — both add fragile shell orchestration for no additional coverage
# signal beyond what the xUnit tests already provide.
#
# NOTE: Swift is intentionally excluded from ALL_SDKS here.
# The Swift SDK byte-equality consistency is verified directly in its XCTest suite
# (TimestampTTSTests.swift), which decodes each shared fixture and asserts toSrt()/toVtt()
# matches the expected/* files exactly via XCTAssertEqual on the String contents.
# Driving the Swift SDK from this Python script would require invoking `swift test` and
# parsing XCTest output, or building a standalone Swift executable with `swift run` — both
# add fragile shell orchestration (Xcode toolchain availability, platform-specific SDK paths)
# for no additional coverage signal beyond what the XCTest suite already provides.
#
# NOTE: Zig is intentionally excluded from ALL_SDKS here.
# The Zig SDK byte-equality consistency is verified directly in its test suite
# (tests/timestamps_test.zig), which deserializes each shared fixture and asserts
# toSrt()/toVtt() matches the expected/* files exactly via expectEqualStrings on the
# string contents.  Driving the Zig SDK from this Python script would require either
# (a) a `zig build test-timestamps` invocation and parsing its output, or (b) building
# a standalone Zig executable with `zig build-exe` — both add fragile shell orchestration
# (Zig toolchain availability, build cache paths) for no additional coverage signal beyond
# what the test suite already provides via `zig build test-timestamps`.
#
# NOTE: PHP is intentionally excluded from ALL_SDKS here.
# The PHP SDK byte-equality consistency is verified directly in its PHPUnit test suite
# (tests/Unit/TimestampTTSTest.php), which deserializes each shared fixture and asserts
# toSrt()/toVtt() matches the expected/* files exactly via assertSame() on the string
# contents.  Driving the PHP SDK from this Python script would require either
# (a) a `php -r` one-liner that bootstraps Composer's autoloader and parses the JSON,
# or (b) a standalone PHP script plus `php <script>` invocation — both add fragile shell
# orchestration (Composer autoload path resolution, PHP binary discovery) for no additional
# coverage signal beyond what the PHPUnit suite already provides via
# `vendor/bin/phpunit tests/Unit/TimestampTTSTest.php`.

FIXTURES = ("both", "word_only", "char_only", "jpn_char")


def run_python(name: str, fmt: str) -> bytes:
    code = f"""
import json, sys
sys.path.insert(0, {json.dumps(str(ROOT / 'typecast-python' / 'src'))})
from typecast.models.tts import TTSWithTimestampsResponse
data = json.loads(open({json.dumps(str(FIX / f'{name}.json'))}, encoding="utf-8").read())
resp = TTSWithTimestampsResponse.model_validate(data)
sys.stdout.buffer.write(resp.to_{fmt}().encode('utf-8'))
"""
    return subprocess.check_output([sys.executable, "-c", code])


def run_js(name: str, fmt: str) -> bytes:
    # The JS build outputs to lib/ (not dist/) and provides both ESM (lib/index.js)
    # and CJS (lib/index.cjs). We use CJS via require() for simplicity.
    dist_path = ROOT / "typecast-js" / "lib" / "index.cjs"
    method = f"to{fmt.title()}"  # toSrt / toVtt
    script = f"""
const {{ WithTimestampsResult }} = require({json.dumps(str(dist_path))});
const fs = require("fs");
const data = JSON.parse(fs.readFileSync({json.dumps(str(FIX / f"{name}.json"))}, "utf-8"));
const r = new WithTimestampsResult(data);
process.stdout.write(Buffer.from(r.{method}(), 'utf-8'));
"""
    return subprocess.check_output(["node", "-e", script])


def run_go(name: str, fmt: str) -> bytes:
    method = "ToSRT" if fmt == "srt" else "ToVTT"
    src_path = ROOT / "typecast-go"
    fixture_path = FIX / f"{name}.json"
    code = f'''package main

import (
    "encoding/json"
    "fmt"
    "io"
    "os"

    typecast "github.com/neosapience/typecast-sdk/typecast-go"
)

func main() {{
    f, err := os.Open({json.dumps(str(fixture_path))})
    if err != nil {{ panic(err) }}
    defer f.Close()
    b, _ := io.ReadAll(f)
    var resp typecast.TTSWithTimestampsResponse
    if err := json.Unmarshal(b, &resp); err != nil {{ panic(err) }}
    out, err := resp.{method}()
    if err != nil {{ panic(err) }}
    fmt.Print(out)
}}
'''
    with tempfile.TemporaryDirectory() as td:
        prog = pathlib.Path(td) / "main.go"
        prog.write_text(code)
        gomod = pathlib.Path(td) / "go.mod"
        # go.mod replace directive needs an unquoted path; src_path is a fixed
        # local path under our control so embedding directly is acceptable.
        gomod.write_text(f"""module consistency_check

go 1.21

require github.com/neosapience/typecast-sdk/typecast-go v0.0.0
replace github.com/neosapience/typecast-sdk/typecast-go => {src_path}
""")
        out = subprocess.check_output(["go", "run", str(prog)], cwd=td)
        return out


def _java_bin(name: str) -> str:
    """Return the path to a JDK binary, searching common Homebrew locations."""
    import shutil
    import os

    # Honour explicit override from environment
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidate = pathlib.Path(java_home) / "bin" / name
        if candidate.exists():
            return str(candidate)

    # Homebrew symlink (works when `brew install openjdk` was run)
    brew_link = pathlib.Path("/opt/homebrew/opt/java/bin") / name
    if brew_link.exists():
        return str(brew_link)

    # System PATH fallback
    found = shutil.which(name)
    if found:
        return found

    raise FileNotFoundError(
        f"Could not locate '{name}'. "
        "Install a JDK (e.g. `brew install openjdk`) or set JAVA_HOME."
    )


def _java_artifact_stale(java_dir: pathlib.Path, target_jar: pathlib.Path) -> bool:
    """Return True if any .java source file is newer than the target jar."""
    if not target_jar.exists():
        return True
    jar_mtime = target_jar.stat().st_mtime
    src_dir = java_dir / "src" / "main"
    for p in src_dir.rglob("*.java"):
        if p.stat().st_mtime > jar_mtime:
            return True
    return False


def run_java(name: str, fmt: str) -> bytes:
    method = "toSrt" if fmt == "srt" else "toVtt"
    java_dir = ROOT / "typecast-java"
    javac = _java_bin("javac")
    java = _java_bin("java")

    # Always rebuild if sources are newer than the jar (or jar doesn't exist).
    # Use glob to find the jar dynamically rather than hardcoding the version.
    target_dir = java_dir / "target"
    candidates = list(target_dir.glob("typecast-java-*.jar")) if target_dir.exists() else []
    candidates = [p for p in candidates if "sources" not in p.name and "javadoc" not in p.name]
    target_jar = max(candidates, key=lambda p: p.stat().st_mtime) if candidates else (target_dir / "typecast-java-PLACEHOLDER.jar")
    if _java_artifact_stale(java_dir, target_jar):
        subprocess.check_call(["mvn", "-q", "package", "-DskipTests"], cwd=str(java_dir))
        # Re-discover jar after build
        candidates = [p for p in target_dir.glob("typecast-java-*.jar")
                      if "sources" not in p.name and "javadoc" not in p.name]
        if not candidates:
            raise FileNotFoundError(f"No typecast-java-*.jar found in {target_dir}")
        target_jar = max(candidates, key=lambda p: p.stat().st_mtime)
    # Always regenerate classpath to ensure it reflects the current build.
    classpath_file = java_dir / "target" / "classpath.txt"
    subprocess.check_call(
        ["mvn", "-q", "dependency:build-classpath", "-Dmdep.outputFile=target/classpath.txt"],
        cwd=str(java_dir),
    )
    classpath = classpath_file.read_text().strip() + os.pathsep + str(target_jar)

    fixture_path = FIX / f"{name}.json"
    with tempfile.TemporaryDirectory() as td:
        driver = pathlib.Path(td) / "Driver.java"
        driver.write_text(
            f"""
import com.google.gson.Gson;
import com.neosapience.models.TTSWithTimestampsResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class Driver {{
    public static void main(String[] args) throws Exception {{
        String json = new String(Files.readAllBytes(Paths.get({json.dumps(str(fixture_path))})), StandardCharsets.UTF_8);
        TTSWithTimestampsResponse resp = new Gson().fromJson(json, TTSWithTimestampsResponse.class);
        System.out.print(resp.{method}());
    }}
}}
"""
        )
        subprocess.check_call([javac, "-cp", classpath, str(driver), "-d", td])
        return subprocess.check_output(
            [java, "-cp", os.pathsep.join([classpath, td]), "Driver"]
        )


RUNNERS = {"python": run_python, "js": run_js, "go": run_go, "java": run_java}


def main() -> int:
    failures = []
    for name in FIXTURES:
        for fmt in ("srt", "vtt"):
            expected_path = FIX / "expected" / f"{name}.{fmt}"
            expected = expected_path.read_bytes()
            for sdk in ALL_SDKS:
                actual = RUNNERS[sdk](name, fmt)
                if actual != expected:
                    failures.append(f"{sdk}/{name}.{fmt}: byte mismatch")
                    # Print a short diff for debugging
                    exp_lines = expected.decode("utf-8", errors="replace").splitlines()
                    act_lines = actual.decode("utf-8", errors="replace").splitlines()
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
