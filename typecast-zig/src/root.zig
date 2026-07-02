pub const Client = @import("client.zig").Client;
pub const models = @import("models.zig");
pub const json_helpers = @import("json.zig");
pub const timestamps = @import("timestamps.zig");
pub const composer = @import("composer.zig");

// Re-export recommendation types at the top level for convenience.
pub const RecommendedVoice = models.RecommendedVoice;

// Re-export quick-cloning types at the top level for convenience.
pub const CustomVoice = models.CustomVoice;
pub const GenerateToFileRequest = models.GenerateToFileRequest;
pub const CLONING_MAX_FILE_SIZE = models.CLONING_MAX_FILE_SIZE;
pub const NAME_MIN_LENGTH = models.NAME_MIN_LENGTH;
pub const NAME_MAX_LENGTH = models.NAME_MAX_LENGTH;

// Re-export timestamp types at the top level for convenience.
pub const AlignmentSegmentWord = timestamps.AlignmentSegmentWord;
pub const AlignmentSegmentCharacter = timestamps.AlignmentSegmentCharacter;
pub const TTSRequestWithTimestamps = timestamps.TTSRequestWithTimestamps;
pub const TTSWithTimestampsResponse = timestamps.TTSWithTimestampsResponse;

// Re-export composed speech helpers at the top level for convenience.
pub const SpeechComposer = composer.SpeechComposer;
pub const ComposerSettings = composer.ComposerSettings;
pub const ComposerOutput = composer.ComposerOutput;
pub const SpeechPart = composer.SpeechPart;
pub const parsePauseMarkup = composer.parsePauseMarkup;
pub const freeSpeechParts = composer.freeSpeechParts;

test {
    @import("std").testing.refAllDecls(@This());
}
