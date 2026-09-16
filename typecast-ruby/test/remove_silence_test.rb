require "minitest/autorun"
require "typecast"

class RemoveSilenceTest < Minitest::Test
  def test_optional_zero_and_range
    [Typecast::Models::Output, Typecast::Models::OutputStream].each do |klass|
      refute klass.new.to_h.key?(:remove_silence_ms)
      [0, 300, 1000].each { |ms| assert_equal ms, klass.new(remove_silence_ms: ms).to_h[:remove_silence_ms] }
      [true, false, "100", 100.0, -1, 1001].each do |ms|
        assert_raises(ArgumentError) { klass.new(remove_silence_ms: ms) }
      end
    end
  end
end
