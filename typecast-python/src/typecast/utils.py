import wave
from math import floor


def show_performance(processing_time: float, wave_path: str):
    with wave.open(wave_path, "rb") as wav:
        frames = wav.getnframes()
        rate = wav.getframerate()

        audio_duration = frames / float(rate)
        num_tokens = floor(audio_duration * 20)
        tokens_per_second = num_tokens / processing_time

        print(f"[Performance] Time taken         : {processing_time:.3f} secs")
        print(f"[Performance] Audio duration     : {audio_duration:.3f} secs")
        print(f"[Performance] Number of tokens   : {num_tokens} tokens")
        print(
            f"[Performance] Tokens per seconds : {tokens_per_second:.3f} tokens / sec"
        )
