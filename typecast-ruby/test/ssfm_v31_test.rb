require "minitest/autorun"
require "typecast"

class SSFMV31Test < Minitest::Test
  def test_model_request
    request = Typecast::Models::TTSRequest.new(voice_id: "tc_test", text: "hello", model: Typecast::Models::TTS_MODEL_V31)
    assert_equal "ssfm-v31", request.to_h[:model]
    assert_equal "ssfm-v30", Typecast::Models::TTS_MODEL_V30
  end
end
