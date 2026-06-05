require "base64"
require "minitest/autorun"
require "tmpdir"

require "typecast"

class TimestampsTest < Minitest::Test
  def test_audio_bytes_and_captions
    response = Typecast::Models::TTSWithTimestampsResponse.from_h(
      "audio" => Base64.strict_encode64("RIFF"),
      "audio_format" => "wav",
      "audio_duration" => 1.0,
      "words" => [
        { "word" => "Hello", "start_time" => 0.0, "end_time" => 0.5 },
        { "word" => "world", "start_time" => 0.5, "end_time" => 1.0 }
      ]
    )

    assert_equal "RIFF", response.audio_bytes
    assert_includes response.to_srt, "00:00:00,000 --> 00:00:00,500"
    assert_includes response.to_vtt, "WEBVTT"

    path = File.join(Dir.tmpdir, "typecast_ruby_test.wav")
    response.save_audio(path)
    assert_equal 4, File.size(path)
  ensure
    File.delete(path) if path && File.exist?(path)
  end

  def test_character_segments_and_missing_segments
    response = Typecast::Models::TTSWithTimestampsResponse.from_h(
      "audio" => Base64.strict_encode64("x"),
      "audio_format" => "wav",
      "audio_duration" => 0.5,
      "characters" => [{ "character" => "A", "start_time" => 0.0, "end_time" => 0.5 }]
    )
    assert_includes response.to_srt, "A"

    empty = Typecast::Models::TTSWithTimestampsResponse.from_h("audio" => "", "audio_format" => "wav", "audio_duration" => 0)
    assert_raises(ArgumentError) { empty.to_srt }
  end
end
