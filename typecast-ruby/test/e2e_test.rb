require "minitest/autorun"

require "typecast"

class E2ETest < Minitest::Test
  def setup
    skip "TYPECAST_API_KEY is required for live API tests" if ENV["TYPECAST_API_KEY"].to_s.empty?
  end

  def test_live_api_lists_voices_and_synthesizes_speech
    client = Typecast::Client.new
    voices = client.get_voices_v2(Typecast::Models::VoicesV2Filter.new(model: Typecast::Models::TTS_MODEL_V30))
    refute_empty voices

    response = client.text_to_speech(
      Typecast::Models::TTSRequest.new(
        voice_id: voices.first.voice_id,
        text: "Hello from the Typecast Ruby SDK.",
        model: Typecast::Models::TTS_MODEL_V30,
        language: "eng"
      )
    )
    refute_empty response.audio_data
  end
end
