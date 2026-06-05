require "base64"

module Typecast
  module Models
    class AlignmentSegmentWord
      attr_reader :word, :start_time, :end_time

      def self.from_h(hash)
        new(word: hash.fetch("word", ""), start_time: hash.fetch("start_time", 0).to_f, end_time: hash.fetch("end_time", 0).to_f)
      end

      def initialize(word:, start_time:, end_time:)
        @word = word
        @start_time = start_time
        @end_time = end_time
      end
    end

    class AlignmentSegmentCharacter
      attr_reader :character, :start_time, :end_time

      def self.from_h(hash)
        new(character: hash.fetch("character", ""), start_time: hash.fetch("start_time", 0).to_f, end_time: hash.fetch("end_time", 0).to_f)
      end

      def initialize(character:, start_time:, end_time:)
        @character = character
        @start_time = start_time
        @end_time = end_time
      end
    end

    class TTSWithTimestampsResponse
      attr_reader :audio, :audio_format, :audio_duration, :words, :characters

      def self.from_h(hash)
        new(
          audio: hash.fetch("audio", ""),
          audio_format: hash.fetch("audio_format", AUDIO_WAV),
          audio_duration: hash.fetch("audio_duration", 0).to_f,
          words: hash.fetch("words", []).map { |item| AlignmentSegmentWord.from_h(item) },
          characters: hash.fetch("characters", []).map { |item| AlignmentSegmentCharacter.from_h(item) }
        )
      end

      def initialize(audio:, audio_format:, audio_duration:, words: [], characters: [])
        @audio = audio
        @audio_format = audio_format
        @audio_duration = audio_duration
        @words = words
        @characters = characters
      end

      def audio_bytes
        Base64.decode64(audio)
      end

      def save_audio(path)
        File.binwrite(path, audio_bytes)
      end

      def to_srt
        caption_lines(webvtt: false)
      end

      def to_vtt
        "WEBVTT\n\n#{caption_lines(webvtt: true)}"
      end

      private

      def caption_lines(webvtt:)
        segments = words.empty? ? characters.map { |c| [c.character, c.start_time, c.end_time] } : words.map { |w| [w.word, w.start_time, w.end_time] }
        raise ArgumentError, "No alignment segments are available" if segments.empty?

        lines = []
        segments.each_with_index do |segment, index|
          lines << (index + 1).to_s unless webvtt
          lines << "#{format_time(segment[1], webvtt)} --> #{format_time(segment[2], webvtt)}"
          lines << segment[0]
          lines << ""
        end
        lines.join("\n")
      end

      def format_time(seconds, webvtt)
        millis = (seconds * 1000).round
        hours = millis / 3_600_000
        minutes = (millis % 3_600_000) / 60_000
        secs = (millis % 60_000) / 1000
        ms = millis % 1000
        format("%02d:%02d:%02d%s%03d", hours, minutes, secs, webvtt ? "." : ",", ms)
      end
    end
  end
end
