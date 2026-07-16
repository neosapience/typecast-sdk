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
      seconds = match[0].to_f
      next unless seconds.finite? && seconds.positive?
      if match_data.begin(0) > last_index
        parts << TextPart.new(kind: "text", text: text[last_index...match_data.begin(0)])
      end
      parts << PausePart.new(kind: "pause", seconds: seconds)
      last_index = match_data.end(0)
    end
    if last_index < text.length
      parts << TextPart.new(kind: "text", text: text[last_index..-1])
    end
    parts
  end

  class SpeechComposer
    def initialize(compose)
      @compose = compose
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

      formats = plan.each_with_object([]) do |part, values|
        format = part.is_a?(Hash) && part[:kind] == "speech" ? part.dig(:settings, :output, :audio_format) : nil
        values << format if format
      end.uniq
      raise ArgumentError, "composed speech segments must use one audio format" if formats.length > 1

      output_format = formats.first || Models::AUDIO_WAV
      unless [Models::AUDIO_WAV, Models::AUDIO_MP3].include?(output_format)
        raise ArgumentError, "unsupported composed speech output format: #{output_format}"
      end

      segments = plan.map do |part|
        part.is_a?(PausePart) ? { type: "pause", duration_seconds: part.seconds } :
          { type: "tts", **request_from_settings(part[:text], part[:settings], output_format).to_h }
      end
      @compose.call(segments)
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

    def request_from_settings(text, settings, output_format)
      output = merge_output(settings[:output], audio_format: output_format)
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

  end
end
