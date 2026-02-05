"""
Advanced TTS example demonstrating emotion, pitch, tempo, and volume customization.
"""

from dotenv import load_dotenv

from typecast.client import Typecast
from typecast.models import Output, Prompt, TTSRequest

# Load environment variables from .env file
load_dotenv()

# Initialize client
cli = Typecast()

# Advanced TTS with custom settings
response = cli.text_to_speech(
    TTSRequest(
        text="I am so excited to show you these amazing features!",
        model="ssfm-v21",
        voice_id="tc_62a8975e695ad26f7fb514d1",
        language="eng",
        # Emotion settings
        prompt=Prompt(
            emotion_preset="happy",  # Options: normal, happy, sad, angry
            emotion_intensity=1.5,  # Range: 0.0 to 2.0
        ),
        # Audio output settings
        output=Output(
            volume=120,  # Range: 0 to 200
            audio_pitch=2,  # Range: -12 to +12 semitones
            audio_tempo=1.2,  # Range: 0.5x to 2.0x
            audio_format="mp3",  # Options: wav, mp3
        ),
        seed=42,  # For reproducible results
    )
)

# Save as MP3
with open("typecast_advanced.mp3", "wb") as f:
    f.write(response.audio_data)

print(f"Audio saved: {response.format}, Duration: {response.duration}s")
