#!/usr/bin/env python3
"""Streaming TTS integration test for typecast-python SDK."""
import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'typecast-python', 'src'))

from typecast import Typecast
from typecast.models import TTSRequestStream, OutputStream

API_KEY = "__pltWfi6S3QGbfLYmNtbF82DiNNxQ7LVNbaEvA6pnCH3"
HOST = "https://api.icepeak.in"
VOICE_ID = "tc_68d259f809700d8ac76e8567"
OUTPUT_FILE = "/tmp/streaming_test_python.wav"

def main():
    client = Typecast(api_key=API_KEY, host=HOST)

    request = TTSRequestStream(
        voice_id=VOICE_ID,
        text="Hello, this is a streaming integration test from the Python SDK.",
        model="ssfm-v30",
        language="eng",
        output=OutputStream(audio_format="wav"),
    )

    print("[Python] Calling text_to_speech_stream...")
    total_bytes = 0
    chunk_count = 0
    with open(OUTPUT_FILE, "wb") as f:
        for chunk in client.text_to_speech_stream(request):
            f.write(chunk)
            total_bytes += len(chunk)
            chunk_count += 1

    print(f"[Python] SUCCESS - {chunk_count} chunks, {total_bytes} bytes -> {OUTPUT_FILE}")
    assert total_bytes > 0, "No audio data received"

if __name__ == "__main__":
    main()
