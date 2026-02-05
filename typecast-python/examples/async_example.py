"""
Async client example demonstrating asynchronous text-to-speech operations.
"""

import asyncio

from dotenv import load_dotenv

from typecast.async_client import AsyncTypecast
from typecast.models import LanguageCode, TTSRequest

# Load environment variables from .env file
load_dotenv()


async def main():
    # Use async context manager
    async with AsyncTypecast() as cli:
        # Convert text to speech
        print("Converting text to speech...")
        response = await cli.text_to_speech(
            TTSRequest(
                text="Hello! This is an asynchronous text-to-speech example.",
                model="ssfm-v21",
                voice_id="tc_62a8975e695ad26f7fb514d1",
                language=LanguageCode.ENG,
            )
        )

        # Save audio file
        with open("typecast_async.wav", "wb") as f:
            f.write(response.audio_data)

        print(f"Audio saved: {response.format}, Duration: {response.duration}s")

        # List voices
        print("\nFetching voices...")
        voices = await cli.voices(model="ssfm-v21")
        print(f"Found {len(voices)} voices")

        # Get specific voice
        if voices:
            voice = await cli.get_voice(voices[0].voice_id)
            print(f"\nVoice details: {voice.voice_name}")


if __name__ == "__main__":
    asyncio.run(main())
