require "typecast"

client = Typecast::Client.new
voice = client.clone_voice(
  audio: File.binread("sample.wav"),
  filename: "sample.wav",
  name: "My Voice",
  model: Typecast::Models::TTS_MODEL_V30
)

puts "Created custom voice: #{voice.voice_id}"
