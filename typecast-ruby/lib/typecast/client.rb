require "json"
require "net/http"
require "securerandom"
require "uri"

require "typecast/errors"
require "typecast/models"
require "typecast/timestamps"

module Typecast
  class Client
    DEFAULT_BASE_URL = "https://api.typecast.ai".freeze

    attr_reader :api_key, :base_url

    def initialize(api_key: ENV["TYPECAST_API_KEY"], base_url: ENV["TYPECAST_API_HOST"] || DEFAULT_BASE_URL, open_timeout: 10, read_timeout: 30)
      @api_key = api_key.to_s.strip
      @base_url = normalize_base_url(base_url)
      validate_api_key!
      @open_timeout = open_timeout
      @read_timeout = read_timeout
    end

    def text_to_speech(request)
      response = request_json(:post, "/v1/text-to-speech", request.to_h)
      Models::TTSResponse.new(
        audio_data: response.body,
        duration: response["X-Audio-Duration"].to_f,
        format: response["Content-Type"].to_s.include?("mp3") || response["Content-Type"].to_s.include?("mpeg") ? Models::AUDIO_MP3 : Models::AUDIO_WAV
      )
    end

    # Browse available API voices at https://typecast.ai/developers/api/voices.
    def generate_to_file(path, text:, voice_id:, model: Models::TTS_MODEL_V30, language: nil, prompt: nil, output: nil, seed: nil)
      request = Models::TTSRequest.new(
        voice_id: voice_id,
        text: text,
        model: model,
        language: language,
        prompt: prompt,
        output: output || inferred_output(path),
        seed: seed
      )
      response = text_to_speech(request)
      File.binwrite(path, response.audio_data)
      response
    end

    def text_to_speech_stream(request)
      response = request_json(:post, "/v1/text-to-speech/stream", request.to_h)
      return enum_for(:text_to_speech_stream, request) unless block_given?

      yield response.body
    end

    def text_to_speech_with_timestamps(request, granularity: nil)
      unless granularity.nil? || %w[word char].include?(granularity)
        raise ArgumentError, "granularity must be 'word' or 'char'"
      end

      path = "/v1/text-to-speech/with-timestamps"
      query = granularity.nil? ? nil : { granularity: granularity }
      response = request_json(:post, path, request.to_h, query)
      Models::TTSWithTimestampsResponse.from_h(JSON.parse(response.body))
    end

    def get_my_subscription
      Models::SubscriptionResponse.from_h(JSON.parse(request_json(:get, "/v1/users/me/subscription").body))
    end

    def get_voices_v2(filter = nil)
      query = filter.respond_to?(:to_h) ? filter.to_h : filter
      JSON.parse(request_json(:get, "/v2/voices", nil, query).body).map do |item|
        Models::VoiceV2.from_h(item)
      end
    end

    def get_voice_v2(voice_id)
      voices = JSON.parse(request_json(:get, "/v2/voices/#{path_segment(voice_id)}").body).map do |item|
        Models::VoiceV2.from_h(item)
      end
      raise NotFoundError, "Voice not found: #{voice_id}" if voices.empty?

      voices.first
    end

    def clone_voice(audio:, filename:, name:, model:)
      validate_clone_inputs(audio, name)
      response = request_multipart("/v1/voices/clone", audio: audio, filename: filename, fields: { name: name, model: model })
      Models::CustomVoice.from_h(JSON.parse(response.body))
    end

    def delete_voice(voice_id)
      request_raw(:delete, "/v1/voices/#{path_segment(voice_id)}")
      nil
    end

    private

    def inferred_output(path)
      case File.extname(path.to_s).downcase
      when ".mp3" then Models::Output.new(audio_format: Models::AUDIO_MP3)
      when ".wav" then Models::Output.new(audio_format: Models::AUDIO_WAV)
      end
    end

    def request_json(method, path, body = nil, query = nil)
      headers = auth_headers.merge("Content-Type" => "application/json")
      request_raw(method, path, body.nil? ? nil : JSON.generate(body), headers, query)
    end

    def request_raw(method, path, body = nil, headers = auth_headers, query = nil)
      uri = build_uri(path, query)
      request = request_for(method, uri)
      headers.each { |key, value| request[key] = value }
      request.body = body unless body.nil?
      response = Net::HTTP.start(uri.hostname, uri.port, use_ssl: uri.scheme == "https") do |http|
        http.open_timeout = @open_timeout
        http.read_timeout = @read_timeout
        http.write_timeout = @read_timeout if http.respond_to?(:write_timeout=)
        http.request(request)
      end
      handle_error(response)
      response
    end

    def request_multipart(path, audio:, filename:, fields:)
      boundary = "typecast-ruby-#{SecureRandom.hex(8)}"
      body = +""
      fields.each do |name, value|
        field_name = disposition_value(name)
        field_value = multipart_body_value(value)
        body << "--#{boundary}\r\n"
        body << "Content-Disposition: form-data; name=\"#{field_name}\"\r\n\r\n"
        body << "#{field_value}\r\n"
      end
      safe_filename = disposition_value(filename)
      body << "--#{boundary}\r\n"
      body << "Content-Disposition: form-data; name=\"file\"; filename=\"#{safe_filename}\"\r\n"
      body << "Content-Type: application/octet-stream\r\n\r\n"
      body << audio
      body << "\r\n--#{boundary}--\r\n"

      request_raw(
        :post,
        path,
        body,
        auth_headers.merge(
          "Content-Type" => "multipart/form-data; boundary=#{boundary}"
        )
      )
    end

    def auth_headers
      api_key.empty? ? {} : { "X-API-KEY" => api_key }
    end

    def validate_api_key!
      return unless api_key.empty? && default_base_url?

      raise ArgumentError, "api_key is required for the default Typecast API host"
    end

    def default_base_url?
      normalized_base_url(base_url).casecmp?(normalized_base_url(DEFAULT_BASE_URL))
    end

    def validate_clone_inputs(audio, name)
      raise ArgumentError, "audio must be 25MB or smaller" if audio.bytesize > Models::CLONING_MAX_FILE_SIZE
      raise ArgumentError, "name must be 1-#{Models::CLONING_NAME_MAX_LENGTH} chars" if name.empty? || name.length > Models::CLONING_NAME_MAX_LENGTH
    end

    def normalize_base_url(value)
      raw = value.to_s
      raw = "https://#{raw}" unless raw.match?(%r{\Ahttps?://}i)
      uri = URI.parse(raw)
      if uri.scheme != "https" && !local_uri?(uri)
        raise ArgumentError, "base_url must use HTTPS"
      end
      uri.to_s
    rescue URI::InvalidURIError
      raise ArgumentError, "base_url must be a valid URL"
    end

    def normalized_base_url(value)
      value.to_s.strip.sub(%r{/+\z}, "")
    end

    def local_uri?(uri)
      uri.scheme == "http" && ["localhost", "127.0.0.1", "::1"].include?(uri.hostname)
    end

    def disposition_value(value)
      string = value.to_s
      raise ArgumentError, "multipart field values must not contain CR or LF" if string.match?(/[\r\n]/)

      string.gsub(/["\\]/) { |character| "\\#{character}" }
    end

    def multipart_body_value(value)
      string = value.to_s
      raise ArgumentError, "multipart field values must not contain CR or LF" if string.match?(/[\r\n]/)

      string
    end

    def path_segment(value)
      URI.encode_www_form_component(value.to_s).gsub("+", "%20")
    end

    def build_uri(path, query)
      uri = URI.join(base_url.end_with?("/") ? base_url : "#{base_url}/", path.sub(%r{\A/}, ""))
      uri.query = URI.encode_www_form(query) if query && !query.empty?
      uri
    end

    def request_for(method, uri)
      case method
      when :get then Net::HTTP::Get.new(uri)
      when :post then Net::HTTP::Post.new(uri)
      when :delete then Net::HTTP::Delete.new(uri)
      else raise ArgumentError, "unsupported method: #{method}"
      end
    end

    def handle_error(response)
      return if response.code.to_i.between?(200, 299)

      detail = extract_detail(response.body)
      error = case response.code.to_i
              when 400 then BadRequestError
              when 401 then UnauthorizedError
              when 402 then PaymentRequiredError
              when 404 then NotFoundError
              when 422 then UnprocessableEntityError
              when 429 then RateLimitError
              when 500 then InternalServerError
              else ApiError
              end
      raise error.new(detail)
    end

    def extract_detail(body)
      parsed = JSON.parse(body)
      parsed["detail"] || parsed["error"] || parsed["message"] || body
    rescue JSON::ParserError
      body
    end
  end
end
