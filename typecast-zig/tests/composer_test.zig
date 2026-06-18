const std = @import("std");
const testing = std.testing;
const typecast = @import("typecast");
const composer = typecast.composer;
const models = typecast.models;

test "parsePauseMarkup splits valid pause tokens and preserves invalid text" {
    const allocator = testing.allocator;
    const parts = try composer.parsePauseMarkup(allocator, "Hello <|0.3s|>world <|bad|> <|3000s|>");
    defer composer.freeSpeechParts(allocator, parts);

    try testing.expectEqual(@as(usize, 5), parts.len);
    try testing.expectEqualStrings("Hello ", parts[0].text);
    try testing.expectEqual(@as(f64, 0.3), parts[1].pause_seconds);
    try testing.expectEqualStrings("world <|bad|> ", parts[2].text);
    try testing.expectEqual(@as(f64, 3000.0), parts[3].pause_seconds);
    try testing.expectEqualStrings("", parts[4].text);
}

test "composer builds per-segment requests with WAV output" {
    const allocator = testing.allocator;
    var client = typecast.Client.init(allocator, .{ .api_key = "test-key", .base_url = "http://localhost:1" });
    defer client.deinit();

    var speech = client.composeSpeech();
    defer speech.deinit();

    try speech.defaults(.{
        .voice_id = "default-voice",
        .model = .ssfm_v30,
        .language = "en",
        .output = .{ .audio_pitch = 1, .audio_tempo = 0.9, .audio_format = .mp3 },
    });
    try speech.say("First", .{});
    try speech.pause(0.25);
    try speech.say("Second", .{
        .voice_id = "second-voice",
        .output = .{ .audio_pitch = -2, .audio_tempo = 1.1 },
    });

    var requests = try speech.segmentRequests();
    defer requests.deinit(allocator);

    try testing.expectEqual(@as(usize, 2), requests.items.len);
    try testing.expectEqualStrings("default-voice", requests.items[0].voice_id);
    try testing.expectEqualStrings("First", requests.items[0].text);
    try testing.expectEqual(models.AudioFormat.wav, requests.items[0].output.?.audio_format.?);
    try testing.expectEqual(@as(?i32, 1), requests.items[0].output.?.audio_pitch);
    try testing.expectEqual(@as(?f64, 0.9), requests.items[0].output.?.audio_tempo);

    try testing.expectEqualStrings("second-voice", requests.items[1].voice_id);
    try testing.expectEqualStrings("Second", requests.items[1].text);
    try testing.expectEqual(models.AudioFormat.wav, requests.items[1].output.?.audio_format.?);
    try testing.expectEqual(@as(?i32, -2), requests.items[1].output.?.audio_pitch);
    try testing.expectEqual(@as(?f64, 1.1), requests.items[1].output.?.audio_tempo);
}

test "composeWav inserts silence between trimmed PCM segments" {
    const allocator = testing.allocator;
    const first = try composer.testWav(allocator, &.{ 0, 100, 0 }, 10);
    defer allocator.free(first);
    const second = try composer.testWav(allocator, &.{ 0, -200, 0 }, 10);
    defer allocator.free(second);

    const combined = try composer.composeWav(allocator, &.{ first, second }, &.{0.2});
    defer allocator.free(combined);

    const pcm = try composer.testReadPcm(allocator, combined);
    defer allocator.free(pcm);

    try testing.expectEqualSlices(i16, &.{ 100, 0, 0, -200 }, pcm);
}
