// Example: text-to-speech with word/character timestamps, SRT and VTT export.
const std = @import("std");
const typecast = @import("typecast");

pub fn main() !void {
    var gpa = std.heap.GeneralPurposeAllocator(.{}){};
    defer _ = gpa.deinit();
    const allocator = gpa.allocator();

    const api_key = std.posix.getenv("TYPECAST_API_KEY") orelse {
        std.debug.print("Error: TYPECAST_API_KEY not set\n", .{});
        std.process.exit(1);
    };
    const base_url = std.posix.getenv("TYPECAST_API_HOST") orelse "https://api.typecast.ai";

    var client = typecast.Client.init(allocator, .{
        .api_key = api_key,
        .base_url = base_url,
    });
    defer client.deinit();

    const request = typecast.timestamps.TTSRequestWithTimestamps{
        .voice_id = "tc_60e5426de8b95f1d3000d7b5",
        .text = "Hello. How are you?",
        .model = .ssfm_v30,
        .language = "eng",
    };

    var resp = try client.textToSpeechWithTimestamps(request, null);
    defer resp.deinit();

    try resp.saveAudio("/tmp/with_timestamps_zig.wav", allocator);

    const srt = try resp.toSrt(allocator);
    defer allocator.free(srt);
    try std.fs.cwd().writeFile(.{
        .sub_path = "/tmp/with_timestamps_zig.srt",
        .data = srt,
    });

    const vtt = try resp.toVtt(allocator);
    defer allocator.free(vtt);
    try std.fs.cwd().writeFile(.{
        .sub_path = "/tmp/with_timestamps_zig.vtt",
        .data = vtt,
    });

    const word_count = if (resp.words) |ws| ws.len else 0;
    const char_count = if (resp.characters) |cs| cs.len else 0;
    std.debug.print("audio: /tmp/with_timestamps_zig.wav ({d:.2}s, format={s})\n", .{
        resp.audio_duration,
        resp.audio_format,
    });
    std.debug.print("words: {d}, characters: {d}\n", .{ word_count, char_count });
    // Print first 3 lines of SRT
    var lines_printed: usize = 0;
    var idx: usize = 0;
    while (idx < srt.len and lines_printed < 3) : (idx += 1) {
        std.debug.print("{c}", .{srt[idx]});
        if (srt[idx] == '\n') lines_printed += 1;
    }
}
