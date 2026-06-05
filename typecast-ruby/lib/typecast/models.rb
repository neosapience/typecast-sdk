module Typecast
  module Models
    TTS_MODEL_V21 = "ssfm-v21"
    TTS_MODEL_V30 = "ssfm-v30"
    AUDIO_WAV = "wav"
    AUDIO_MP3 = "mp3"
    CLONING_MAX_FILE_SIZE = 25 * 1024 * 1024
    CLONING_NAME_MAX_LENGTH = 30

    LANGUAGE_CODES = %w[
      eng kor jpn spa deu fra ita pol nld rus ell tam tgl fin zho slk ara hrv
      ukr ind dan swe msa ces por bul ron ben hin hun nan nor pan tha tur vie yue
    ].freeze

    class Output
      attr_reader :volume, :target_lufs, :audio_pitch, :audio_tempo, :audio_format

      def initialize(volume: nil, target_lufs: nil, audio_pitch: nil, audio_tempo: nil, audio_format: nil)
        @volume = volume
        @target_lufs = target_lufs
        @audio_pitch = audio_pitch
        @audio_tempo = audio_tempo
        @audio_format = audio_format
      end

      def to_h
        Models.compact(
          volume: volume,
          target_lufs: target_lufs,
          audio_pitch: audio_pitch,
          audio_tempo: audio_tempo,
          audio_format: audio_format
        )
      end
    end

    class OutputStream
      attr_reader :audio_pitch, :audio_tempo, :audio_format

      def initialize(audio_pitch: nil, audio_tempo: nil, audio_format: nil)
        @audio_pitch = audio_pitch
        @audio_tempo = audio_tempo
        @audio_format = audio_format
      end

      def to_h
        Models.compact(audio_pitch: audio_pitch, audio_tempo: audio_tempo, audio_format: audio_format)
      end
    end

    class Prompt
      attr_reader :emotion_preset, :emotion_intensity

      def initialize(emotion_preset: nil, emotion_intensity: nil)
        @emotion_preset = emotion_preset
        @emotion_intensity = emotion_intensity
      end

      def to_h
        Models.compact(emotion_preset: emotion_preset, emotion_intensity: emotion_intensity)
      end
    end

    class PresetPrompt
      attr_reader :emotion_preset, :emotion_intensity

      def initialize(emotion_preset: nil, emotion_intensity: nil)
        @emotion_preset = emotion_preset
        @emotion_intensity = emotion_intensity
      end

      def to_h
        Models.compact(emotion_type: "preset", emotion_preset: emotion_preset, emotion_intensity: emotion_intensity)
      end
    end

    class SmartPrompt
      attr_reader :previous_text, :next_text

      def initialize(previous_text: nil, next_text: nil)
        @previous_text = previous_text
        @next_text = next_text
      end

      def to_h
        Models.compact(emotion_type: "smart", previous_text: previous_text, next_text: next_text)
      end
    end

    class TTSRequest
      attr_reader :voice_id, :text, :model, :language, :prompt, :output, :seed

      def initialize(voice_id:, text:, model:, language: nil, prompt: nil, output: nil, seed: nil)
        @voice_id = voice_id
        @text = text
        @model = model
        @language = language
        @prompt = prompt
        @output = output
        @seed = seed
      end

      def to_h
        Models.compact(
          voice_id: voice_id,
          text: text,
          model: model,
          language: language,
          prompt: Models.value_to_h(prompt),
          output: Models.value_to_h(output),
          seed: seed
        )
      end
    end

    class TTSRequestStream < TTSRequest
    end

    class TTSResponse
      attr_reader :audio_data, :duration, :format

      def initialize(audio_data:, duration:, format:)
        @audio_data = audio_data
        @duration = duration
        @format = format
      end
    end

    class SubscriptionResponse
      attr_reader :plan, :plan_credits, :used_credits, :concurrency_limit

      def self.from_h(hash)
        credits = hash.fetch("credits", {})
        limits = hash.fetch("limits", {})
        new(
          plan: hash.fetch("plan", ""),
          plan_credits: credits.fetch("plan_credits", 0),
          used_credits: credits.fetch("used_credits", 0),
          concurrency_limit: limits.fetch("concurrency_limit", 0)
        )
      end

      def initialize(plan:, plan_credits:, used_credits:, concurrency_limit:)
        @plan = plan
        @plan_credits = plan_credits
        @used_credits = used_credits
        @concurrency_limit = concurrency_limit
      end
    end

    class VoiceV2
      attr_reader :voice_id, :voice_name, :models, :gender, :age, :use_cases

      def self.from_h(hash)
        new(
          voice_id: hash.fetch("voice_id", ""),
          voice_name: hash.fetch("voice_name", ""),
          models: hash.fetch("models", []),
          gender: hash["gender"],
          age: hash["age"],
          use_cases: hash.fetch("use_cases", [])
        )
      end

      def initialize(voice_id:, voice_name:, models:, gender: nil, age: nil, use_cases: [])
        @voice_id = voice_id
        @voice_name = voice_name
        @models = models
        @gender = gender
        @age = age
        @use_cases = use_cases
      end
    end

    class VoicesV2Filter
      attr_reader :model, :gender, :age, :use_cases

      def initialize(model: nil, gender: nil, age: nil, use_cases: nil)
        @model = model
        @gender = gender
        @age = age
        @use_cases = use_cases
      end

      def to_h
        Models.compact(model: model, gender: gender, age: age, use_cases: use_cases&.join(","))
      end
    end

    class CustomVoice
      attr_reader :voice_id, :name, :model

      def self.from_h(hash)
        new(voice_id: hash.fetch("voice_id", ""), name: hash.fetch("name", ""), model: hash["model"])
      end

      def initialize(voice_id:, name:, model: nil)
        @voice_id = voice_id
        @name = name
        @model = model
      end
    end

    def self.compact(hash)
      hash.reject { |_key, value| value.nil? }
    end

    def self.value_to_h(value)
      return nil if value.nil?
      return value.to_h if value.respond_to?(:to_h)
      value
    end

  end
end
