require "typecast"

client = Typecast::Client.new
response = client.text_to_speech(
  Typecast::Models::TTSRequest.new(
    voice_id: "tc_60e5426de8b95f1d3000d7b5",
    text: "Hello from Typecast Ruby.",
    model: Typecast::Models::TTS_MODEL_V30,
    language: "eng",
    output: Typecast::Models::Output.new(audio_format: "wav")
  )
)

File.binwrite("hello.wav", response.audio_data)
