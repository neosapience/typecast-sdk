require "json"
require "minitest/autorun"
require "socket"
require "thread"

require "typecast"

class ComposerTest < Minitest::Test
  def test_compose_speech_composes_wav_and_merges_overrides
    responses = [
      wav_with_samples([0, 1000, 2000, 0]),
      wav_with_samples([0, -1000, -2000, 0])
    ]

    with_server(responses) do |url, captured|
      client = Typecast::Client.new(api_key: "key", base_url: url)
      response = client
        .compose_speech
        .defaults(
          voice_id: "voice-a",
          model: Typecast::Models::TTS_MODEL_V30,
          language: "eng",
          output: Typecast::Models::Output.new(audio_format: "wav", audio_pitch: 1)
        )
        .say("Hello<|0.001s|>world", voice_id: "voice-b", output: Typecast::Models::Output.new(audio_tempo: 1.1))
        .generate

      first = JSON.parse(captured.pop[/\r\n\r\n(.*)\z/m, 1])
      second = JSON.parse(captured.pop[/\r\n\r\n(.*)\z/m, 1])
      assert_equal "Hello", first["text"]
      assert_equal "world", second["text"]
      assert_equal "voice-b", first["voice_id"]
      assert_equal "wav", first.dig("output", "audio_format")
      assert_equal 1, first.dig("output", "audio_pitch")
      assert_equal 1.1, first.dig("output", "audio_tempo")

      assert_equal "wav", response.format
      assert_equal [1000, 2000, 0, -1000, -2000], samples_from_wav(response.audio_data)
      assert_in_delta 0.005, response.duration, 0.0001
    end
  end

  def test_compose_speech_validates_before_network
    client = Typecast::Client.new(api_key: "key", base_url: "http://127.0.0.1")

    error = assert_raises(ArgumentError) do
      client.compose_speech.say("Hello").generate
    end
    assert_match(/voice_id is required/, error.message)

    assert_raises(ArgumentError) do
      client.compose_speech.pause(0)
    end
  end

  def test_parse_pause_markup_is_lenient_for_invalid_tokens
    parts = Typecast.parse_pause_markup("a<|0.3s|>b<|abc|>c<|3s|>")

    assert_equal ["text", "pause", "text", "pause"], parts.map(&:kind)
    assert_equal "a", parts[0].text
    assert_equal 0.3, parts[1].seconds
    assert_equal "b<|abc|>c", parts[2].text
    assert_equal 3, parts[3].seconds
  end

  def test_compose_speech_rejects_bad_wav_unsupported_format_and_mp3
    with_server(["not wav"]) do |url, _captured|
      client = Typecast::Client.new(api_key: "key", base_url: url)
      assert_raises(ArgumentError) do
        client.compose_speech.defaults(voice_id: "voice-a", model: Typecast::Models::TTS_MODEL_V30).say("Hello").generate
      end
    end

    client = Typecast::Client.new(api_key: "key", base_url: "http://127.0.0.1")
    assert_raises(ArgumentError) do
      client.compose_speech.defaults(
        voice_id: "voice-a",
        model: Typecast::Models::TTS_MODEL_V30,
        output: Typecast::Models::Output.new(audio_format: "flac")
      ).say("Hello").generate
    end

    with_server([wav_with_samples([1000])]) do |url, captured|
      client = Typecast::Client.new(api_key: "key", base_url: url)
      error = assert_raises(ArgumentError) do
        client.compose_speech.defaults(
          voice_id: "voice-a",
          model: Typecast::Models::TTS_MODEL_V30,
          output: Typecast::Models::Output.new(audio_format: "mp3")
        ).say("Hello").generate
      end

      assert_match(/ffmpeg is required/, error.message)
      assert_equal Typecast::Models::AUDIO_WAV, captured_format(captured.pop)
    end
  end

  private

  def with_server(response_bodies, &block)
    server = TCPServer.new("127.0.0.1", 0)
    port = server.addr[1]
    captured = Queue.new
    thread = Thread.new do
      response_bodies.each do |response_body|
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
        socket.write "Content-Type: audio/wav\r\n"
        socket.write "X-Audio-Duration: 1\r\n"
        socket.write "Content-Length: #{response_body.bytesize}\r\n"
        socket.write "\r\n"
        socket.write response_body
        socket.close
      end
    rescue IOError
      nil
    end
    yield "http://127.0.0.1:#{port}", captured
  ensure
    server&.close
    thread&.join(1)
  end

  def captured_format(request)
    JSON.parse(request[/\r\n\r\n(.*)\z/m, 1]).dig("output", "audio_format")
  end

  def wav_with_samples(samples, sample_rate: 1000)
    payload = samples.pack("s<*")
    [
      "RIFF",
      [36 + payload.bytesize].pack("V"),
      "WAVE",
      "fmt ",
      [16, 1, 1, sample_rate, sample_rate * 2, 2, 16].pack("VvvVVvv"),
      "data",
      [payload.bytesize].pack("V"),
      payload
    ].join
  end

  def samples_from_wav(data)
    offset = data.index("data") + 8
    data[offset..-1].unpack("s<*")
  end
end
