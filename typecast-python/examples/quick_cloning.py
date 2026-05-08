"""Instant cloning: clone -> speak -> delete in one flow.

Usage:
    export TYPECAST_API_KEY="your-api-key"
    export TYPECAST_API_HOST="https://api.icepeak.in"  # dev only, omit for prod
    python examples/quick_cloning.py path/to/sample.wav

The script clones the voice, synthesizes a short greeting with it,
saves the output to ``cloned_output.wav``, and deletes the cloned voice.
"""
from __future__ import annotations

import sys
from pathlib import Path

from typecast import Typecast
from typecast.models import TTSRequest


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: python examples/quick_cloning.py <audio.wav>", file=sys.stderr)
        return 1

    audio_path = Path(sys.argv[1])
    if not audio_path.exists():
        print(f"audio file not found: {audio_path}", file=sys.stderr)
        return 1

    client = Typecast()  # picks up TYPECAST_API_KEY / TYPECAST_API_HOST from env

    print(f"[1/3] Cloning voice from {audio_path}...")
    voice = client.clone_voice(audio=audio_path, name="demo-voice", model="ssfm-v30")
    print(f"  -> {voice.voice_id}")

    try:
        print("[2/3] Synthesizing greeting with the cloned voice...")
        out = client.text_to_speech(TTSRequest(
            text="Hello! This is my cloned voice.",
            voice_id=voice.voice_id,
            model="ssfm-v30",
        ))
        Path("cloned_output.wav").write_bytes(out.audio_data)
        print("  -> cloned_output.wav")
    finally:
        print("[3/3] Deleting cloned voice...")
        client.delete_voice(voice.voice_id)
        print("  -> done")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
