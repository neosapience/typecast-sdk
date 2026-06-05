require "json"
require "minitest/autorun"
require "socket"
require "thread"

require "typecast"

class ClientTest < Minitest::Test
  def with_server(response_status: 200, response_headers: {}, response_body: "", &block)
    server = TCPServer.new("127.0.0.1", 0)
    port = server.addr[1]
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
      headers = { "Content-Type" => "application/json" }.merge(response_headers)
      socket.write "HTTP/1.1 #{response_status} OK\r\n"
      headers.each { |key, value| socket.write "#{key}: #{value}\r\n" }
      socket.write "Content-Length: #{response_body.bytesize}\r\n"
      socket.write "\r\n"
      socket.write response_body
      socket.close
    end
    yield "http://127.0.0.1:#{port}", captured
  ensure
    server&.close
    thread&.join(1)
  end

  def test_base_url_requires_https_for_remote_hosts
    client = Typecast::Client.new(api_key: "key", base_url: "api.example.com")
    assert_equal "https://api.example.com", client.base_url

    assert_raises(ArgumentError) do
      Typecast::Client.new(api_key: "key", base_url: "http://api.example.com")
    end
  end

  def test_text_to_speech_posts_json_and_parses_audio
    with_server(response_headers: { "Content-Type" => "audio/wav", "X-Audio-Duration" => "1.25" }, response_body: "WAV") do |url, captured|
      client = Typecast::Client.new(api_key: "key", base_url: url)
      response = client.text_to_speech(
        Typecast::Models::TTSRequest.new(
          voice_id: "tc_123",
          text: "Hello",
          model: Typecast::Models::TTS_MODEL_V30,
          language: "eng",
          prompt: Typecast::Models::PresetPrompt.new(emotion_preset: "normal"),
          output: Typecast::Models::Output.new(audio_format: "wav"),
          seed: 42
        )
      )

      assert_equal "WAV", response.audio_data
      assert_equal 1.25, response.duration
      assert_equal "wav", response.format
      request = captured.pop
      assert_includes request, "POST /v1/text-to-speech"
      assert_match(/^X-Api-Key: key\r?$/im, request, "expected API key header")
      assert_includes request, "\"language\":\"eng\""
    end
  end

  def test_error_mapping
    with_server(response_status: 400, response_body: JSON.generate("detail" => "bad")) do |url, _captured|
      client = Typecast::Client.new(api_key: "key", base_url: url)
      assert_raises(Typecast::BadRequestError) do
        client.get_my_subscription
      end
    end
  end

  def test_subscription_and_voices
    body = JSON.generate(
      "plan" => "plus",
      "credits" => { "plan_credits" => 1000, "used_credits" => 10 },
      "limits" => { "concurrency_limit" => 3 }
    )
    with_server(response_body: body) do |url, _captured|
      client = Typecast::Client.new(api_key: "key", base_url: url)
      subscription = client.get_my_subscription
      assert_equal "plus", subscription.plan
      assert_equal 3, subscription.concurrency_limit
    end

    voices_body = JSON.generate([
      {
        "voice_id" => "tc_123",
        "voice_name" => "Voice",
        "models" => [{ "version" => "ssfm-v30", "emotions" => ["normal"] }],
        "gender" => "female",
        "age" => "young_adult",
        "use_cases" => ["Podcast"]
      }
    ])
    with_server(response_body: voices_body) do |url, _captured|
      client = Typecast::Client.new(api_key: "key", base_url: url)
      voices = client.get_voices_v2(Typecast::Models::VoicesV2Filter.new(model: "ssfm-v30"))
      assert_equal "tc_123", voices.first.voice_id
    end
  end

  def test_get_voice_v2_not_found
    with_server(response_body: "[]") do |url, captured|
      client = Typecast::Client.new(api_key: "key", base_url: url)
      assert_raises(Typecast::NotFoundError) { client.get_voice_v2("tc/missing voice") }
      assert_includes captured.pop, "GET /v2/voices/tc%2Fmissing%20voice"
    end
  end

  def test_clone_voice_validates_and_posts_multipart
    with_server(response_body: JSON.generate("voice_id" => "uc_123", "name" => "Mine", "model" => "ssfm-v30")) do |url, captured|
      client = Typecast::Client.new(api_key: "key", base_url: url)
      voice = client.clone_voice(audio: "abc", filename: "sample.wav", name: "Mine", model: "ssfm-v30")
      assert_equal "uc_123", voice.voice_id
      request = captured.pop
      assert_includes request, "POST /v1/voices/clone"
      assert_includes request, "multipart/form-data"
      assert_includes request, "name=\"file\""
    end

    client = Typecast::Client.new(api_key: "key", base_url: "http://127.0.0.1")
    assert_raises(ArgumentError) do
      client.clone_voice(audio: "x" * (Typecast::Models::CLONING_MAX_FILE_SIZE + 1), filename: "a.wav", name: "Mine", model: "ssfm-v30")
    end

    assert_raises(ArgumentError) do
      client.clone_voice(audio: "abc", filename: "sample.wav\r\nX-Bad: 1", name: "Mine", model: "ssfm-v30")
    end

    assert_raises(ArgumentError) do
      client.clone_voice(audio: "abc", filename: "sample.wav", name: "Bad\r\nName", model: "ssfm-v30")
    end
  end

  def test_delete_voice_accepts_204
    with_server(response_status: 204) do |url, captured|
      client = Typecast::Client.new(api_key: "key", base_url: url)
      client.delete_voice("uc/123")
      assert_includes captured.pop, "DELETE /v1/voices/uc%2F123"
    end
  end
end
