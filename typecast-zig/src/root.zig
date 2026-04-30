pub const Client = @import("client.zig").Client;
pub const models = @import("models.zig");
pub const json_helpers = @import("json.zig");
pub const timestamps = @import("timestamps.zig");

// Re-export timestamp types at the top level for convenience.
pub const AlignmentSegmentWord = timestamps.AlignmentSegmentWord;
pub const AlignmentSegmentCharacter = timestamps.AlignmentSegmentCharacter;
pub const TTSRequestWithTimestamps = timestamps.TTSRequestWithTimestamps;
pub const TTSWithTimestampsResponse = timestamps.TTSWithTimestampsResponse;

test {
    @import("std").testing.refAllDecls(@This());
}
