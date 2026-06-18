require "stringio"

require "typecast/models"

module Typecast
  PausePart = Struct.new(:kind, :seconds, keyword_init: true)
  TextPart = Struct.new(:kind, :text, keyword_init: true)

  PAUSE_TOKEN = /<\|(\d+(?:\.\d+)?)s\|>/.freeze

  def self.parse_pause_markup(text)
    parts = []
    last_index = 0
    text.to_s.scan(PAUSE_TOKEN) do |match|
      match_data = Regexp.last_match
      if match_data.begin(0) > last_index
        parts << TextPart.new(kind: "text", text: text[last_index...match_data.begin(0)])
      end
      parts << PausePart.new(kind: "pause", seconds: match[0].to_f)
      last_index = match_data.end(0)
    end
    if last_index < text.length
      parts << TextPart.new(kind: "text", text: text[last_index..-1])
    end
    parts
  end

  class SpeechComposer
    def initialize(text_to_speech)
      @text_to_speech = text_to_speech
      @defaults = {}
      @parts = []
    end

    def defaults(voice_id: nil, model: nil, language: nil, prompt: nil, output: nil, seed: nil)
      @defaults = merge_settings(@defaults, settings_hash(
        voice_id: voice_id,
        model: model,
        language: language,
        prompt: prompt,
        output: output,
        seed: seed
      ))
      self
    end

    def say(text, voice_id: nil, model: nil, language: nil, prompt: nil, output: nil, seed: nil)
      @parts << {
        kind: "speech",
        text: text.to_s,
        settings: merge_settings(@defaults, settings_hash(
          voice_id: voice_id,
          model: model,
          language: language,
          prompt: prompt,
          output: output,
          seed: seed
        ))
      }
      self
    end

    # Inserts silence between speech segments.
    #
    # seconds is a duration in seconds. Use 0.3 for 300 ms, 3 for 3 seconds.
    def pause(seconds)
      unless seconds.is_a?(Numeric) && seconds.finite? && seconds.positive?
        raise ArgumentError, "pause seconds must be greater than 0"
      end

      @parts << PausePart.new(kind: "pause", seconds: seconds.to_f)
      self
    end

    def generate
      plan = build_plan
      unless plan.any? { |part| part.is_a?(Hash) && part[:kind] == "speech" }
        raise ArgumentError, "at least one speech segment is required"
      end

      output_format = @defaults.dig(:output, :audio_format) || Models::AUDIO_WAV
      unless [Models::AUDIO_WAV, Models::AUDIO_MP3].include?(output_format)
        raise ArgumentError, "unsupported composed speech output format: #{output_format}"
      end

      wav_spec = nil
      output_samples = []
      plan.each do |part|
        if part.is_a?(PausePart)
          raise ArgumentError, "pause cannot be the first composed part" if wav_spec.nil?

          output_samples.concat(Array.new(seconds_to_samples(part.seconds, wav_spec[:sample_rate]), 0))
          next
        end

        response = @text_to_speech.call(request_from_settings(part[:text], part[:settings]))
        wav = parse_wav(response.audio_data)
        if wav_spec && wav[:spec] != wav_spec
          raise ArgumentError, "all composed WAV segments must use the same PCM format"
        end

        wav_spec = wav[:spec]
        output_samples.concat(trim_silence(wav[:samples]))
      end

      wav_data = encode_wav(output_samples, wav_spec)
      raise ArgumentError, "ffmpeg is required to encode composed speech as mp3" if output_format == Models::AUDIO_MP3

      Models::TTSResponse.new(
        audio_data: wav_data,
        duration: output_samples.length.to_f / wav_spec[:sample_rate],
        format: Models::AUDIO_WAV
      )
    end

    private

    def build_plan
      plan = []
      @parts.each do |part|
        if part.is_a?(PausePart)
          plan << part
          next
        end

        Typecast.parse_pause_markup(part[:text]).each do |parsed|
          if parsed.is_a?(PausePart)
            plan << parsed
            next
          end
          next if parsed.text.strip.empty?

          raise ArgumentError, "voice_id is required for composed speech segments" if part[:settings][:voice_id].to_s.empty?
          raise ArgumentError, "model is required for composed speech segments" if part[:settings][:model].to_s.empty?

          plan << { kind: "speech", text: parsed.text, settings: part[:settings] }
        end
      end
      plan
    end

    def settings_hash(voice_id:, model:, language:, prompt:, output:, seed:)
      {
        voice_id: voice_id,
        model: model,
        language: language,
        prompt: prompt,
        output: output_hash(output),
        seed: seed
      }.reject { |_key, value| value.nil? }
    end

    def merge_settings(base, override)
      merged = base.merge(override)
      merged[:output] = merge_output(base[:output], override[:output])
      merged.reject { |_key, value| value.nil? }
    end

    def merge_output(base, override)
      return nil if base.nil? && override.nil?

      (base || {}).merge(override || {})
    end

    def output_hash(output)
      return nil if output.nil?
      return output.to_h if output.respond_to?(:to_h)

      output
    end

    def request_from_settings(text, settings)
      output = merge_output(settings[:output], audio_format: Models::AUDIO_WAV)
      Models::TTSRequest.new(
        voice_id: settings[:voice_id],
        text: text,
        model: settings[:model],
        language: settings[:language],
        prompt: settings[:prompt],
        output: Models::Output.new(**output),
        seed: settings[:seed]
      )
    end

    def parse_wav(data)
      io = StringIO.new(data)
      raise ArgumentError, "unsupported WAV data" unless io.read(4) == "RIFF"

      io.read(4)
      raise ArgumentError, "unsupported WAV data" unless io.read(4) == "WAVE"

      spec = nil
      samples = nil
      until io.eof?
        chunk_id = io.read(4)
        break if chunk_id.nil? || chunk_id.bytesize < 4

        chunk_size_bytes = io.read(4)
        raise ArgumentError, "unsupported WAV data" if chunk_size_bytes.nil? || chunk_size_bytes.bytesize < 4

        chunk_size = chunk_size_bytes.unpack1("V")
        chunk_data = io.read(chunk_size)
        io.read(1) if chunk_size.odd?
        raise ArgumentError, "unsupported WAV data" if chunk_data.nil? || chunk_data.bytesize < chunk_size

        case chunk_id
        when "fmt "
          audio_format, channels, sample_rate, _byte_rate, _block_align, bits_per_sample = chunk_data.unpack("vvVVvv")
          if audio_format != 1 || channels != 1 || bits_per_sample != 16
            raise ArgumentError, "only mono 16-bit PCM WAV is supported for composed speech"
          end
          spec = { sample_rate: sample_rate, channels: channels, bits_per_sample: bits_per_sample }
        when "data"
          samples = chunk_data.unpack("s<*")
        end
      end

      raise ArgumentError, "unsupported WAV data" if spec.nil? || samples.nil?

      { spec: spec, samples: samples }
    end

    def encode_wav(samples, spec)
      payload = samples.pack("s<*")
      [
        "RIFF",
        [36 + payload.bytesize].pack("V"),
        "WAVE",
        "fmt ",
        [16, 1, spec[:channels], spec[:sample_rate], spec[:sample_rate] * spec[:channels] * 2, spec[:channels] * 2, spec[:bits_per_sample]].pack("VvvVVvv"),
        "data",
        [payload.bytesize].pack("V"),
        payload
      ].join
    end

    def trim_silence(samples)
      start_index = 0
      end_index = samples.length
      start_index += 1 while start_index < end_index && samples[start_index].abs <= 0
      end_index -= 1 while end_index > start_index && samples[end_index - 1].abs <= 0
      samples[start_index...end_index] || []
    end

    def seconds_to_samples(seconds, sample_rate)
      (seconds * sample_rate).round
    end
  end
end
