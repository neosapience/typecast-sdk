"""
Example demonstrating how to list and retrieve voice information.
"""

from dotenv import load_dotenv

from typecast.client import Typecast

# Load environment variables from .env file
load_dotenv()

# Initialize client
cli = Typecast()

# List all available voices
print("Listing all voices...")
voices = cli.voices()

print(f"\nFound {len(voices)} voices:\n")
for voice in voices[:5]:  # Show first 5
    print(f"  ID: {voice.voice_id}")
    print(f"  Name: {voice.voice_name}")
    print(f"  Model: {voice.model}")
    print(f"  Emotions: {', '.join(voice.emotions)}")
    print()

# Filter voices by model
print("\nListing voices for model 'ssfm-v21'...")
v21_voices = cli.voices(model="ssfm-v21")
print(f"Found {len(v21_voices)} voices for ssfm-v21")

# Get specific voice by ID
print("\nGetting specific voice...")
voice = cli.get_voice("tc_62a8975e695ad26f7fb514d1")
print(f"Voice: {voice.voice_name}")
print(f"Model: {voice.model}")
print(f"Available emotions: {', '.join(voice.emotions)}")
