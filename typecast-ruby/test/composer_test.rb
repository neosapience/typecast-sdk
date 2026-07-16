require "json"
require "minitest/autorun"
require "socket"
require "thread"

require "typecast"

class ComposerTest < Minitest::Test
  def test_compose_speech_uses_compose_api_and_merges_overrides
    with_server("composed-audio", content_type: "audio/mpeg") do |url, captured|
      response = Typecast::Client.new(api_key: "key", base_url: url)
        .compose_speech
        .defaults(
          voice_id: "voice-a",
          model: Typecast::Models::TTS_MODEL_V30,
          output: Typecast::Models::Output.new(audio_format: "mp3", audio_pitch: 1)
        )
        .say(
          "Hello<|0.3s|>world",
          voice_id: "voice-b",
          output: Typecast::Models::Output.new(audio_format: nil, audio_pitch: nil, audio_tempo: 1.1)
        )
        .generate

      request = captured.pop
      assert_match(%r{POST /v1/text-to-speech/compose HTTP/1\.1}, request)
      body = JSON.parse(request[/\r\n\r\n(.*)\z/m, 1])
      segments = body.fetch("segments")
      assert_equal %w[tts pause tts], segments.map { |segment| segment.fetch("type") }
      assert_equal "Hello", segments[0]["text"]
      assert_equal "voice-b", segments[0]["voice_id"]
      assert_equal "mp3", segments[0].dig("output", "audio_format")
      assert_equal 1, segments[0].dig("output", "audio_pitch")
      assert_equal 1.1, segments[0].dig("output", "audio_tempo")
      assert_equal 0.3, segments[1]["duration_seconds"]
      assert_equal "world", segments[2]["text"]
      assert_equal "composed-audio", response.audio_data
      assert_equal "mp3", response.format
      assert_in_delta 1.25, response.duration, 0.0001
    end
  end

  def test_compose_speech_validates_before_network
    client = Typecast::Client.new(api_key: "key", base_url: "http://127.0.0.1")
    error = assert_raises(ArgumentError) { client.compose_speech.say("Hello").generate }
    assert_match(/voice_id is required/, error.message)
    assert_raises(ArgumentError) { client.compose_speech.pause(0) }
  end

  def test_parse_pause_markup_is_lenient_for_invalid_tokens
    parts = Typecast.parse_pause_markup("a<|0.3s|>b<|abc|>c<|3s|>")
    assert_equal ["text", "pause", "text", "pause"], parts.map(&:kind)
    assert_equal "a", parts[0].text
    assert_equal 0.3, parts[1].seconds
    assert_equal "b<|abc|>c", parts[2].text
    assert_equal 3, parts[3].seconds
  end

  private

  def with_server(response_body, content_type: "audio/wav")
    server = TCPServer.new("127.0.0.1", 0)
    captured = Queue.new
    thread = Thread.new do
      socket = server.accept
      request = +""
      while (line = socket.gets)
        request << line
        break if line == "\r\n"
      end
      length = request[/Content-Length: (\d+)/i, 1].to_i
      request << socket.read(length).to_s if length.positive?
      captured << request
      socket.write "HTTP/1.1 200 OK\r\n"
      socket.write "Content-Type: #{content_type}\r\n"
      socket.write "X-Audio-Duration: 1.25\r\n"
      socket.write "Content-Length: #{response_body.bytesize}\r\n\r\n"
      socket.write response_body
      socket.close
    rescue IOError
      nil
    end
    yield "http://127.0.0.1:#{server.addr[1]}", captured
  ensure
    server&.close
    thread&.join(1)
  end
end
