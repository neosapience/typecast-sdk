import time

from dotenv import load_dotenv

from typecast.client import Typecast
from typecast.models import TTSRequest
from typecast.utils import show_performance

# Load environment variables from .env file
load_dotenv()

# Initialize client
cli = Typecast()

# Convert text to speech
start_ts = time.time()
res = cli.text_to_speech(
    TTSRequest(
        text="Hello there! I'm your friendly text-to-speech agent. I can help you convert any text into natural sounding speech. I support multiple languages and voices, and I can even adjust the pitch and volume of the generated audio. Would you like to try it out?",
        model="ssfm-v21",
        voice_id="tc_62a8975e695ad26f7fb514d1",  # Note: Voice ID should start with 'tc_' or 'uc_'
    )
)
duration = time.time() - start_ts

# Save audio to file
with open("typecast.wav", "wb") as f:
    f.write(res.audio_data)

show_performance(duration, "typecast.wav")
