require "typecast"

client = Typecast::Client.new
response = client.text_to_speech_with_timestamps(
  Typecast::Models::TTSRequest.new(
    voice_id: "tc_60e5426de8b95f1d3000d7b5",
    text: "Hello with timestamps.",
    model: Typecast::Models::TTS_MODEL_V30,
    language: "eng"
  ),
  granularity: "word"
)

response.save_audio("hello.wav")
puts response.to_srt
