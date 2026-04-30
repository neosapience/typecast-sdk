"""Generate speech with word/character timestamps and export SRT/VTT."""
import os

from typecast import Typecast
from typecast.models import TTSRequestWithTimestamps


def main() -> None:
    client = Typecast(api_key=os.environ["TYPECAST_API_KEY"])
    request = TTSRequestWithTimestamps(
        voice_id="tc_60e5426de8b95f1d3000d7b5",
        text="Hello. How are you?",
        model="ssfm-v30",
        language="eng",
    )
    resp = client.text_to_speech_with_timestamps(request)
    resp.save_audio("hello.wav")
    with open("hello.srt", "w", encoding="utf-8") as f:
        f.write(resp.to_srt())
    with open("hello.vtt", "w", encoding="utf-8") as f:
        f.write(resp.to_vtt())
    print(f"audio: hello.wav ({resp.audio_duration:.2f}s, format={resp.audio_format})")
    print(f"words: {len(resp.words or [])}, characters: {len(resp.characters or [])}")


if __name__ == "__main__":
    main()
