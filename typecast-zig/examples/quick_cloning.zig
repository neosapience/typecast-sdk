/// Instant cloning example for the Typecast Zig SDK.
///
/// Usage:
///   TYPECAST_API_KEY=<key> zig build example-quick-cloning
///
/// The example reads a WAV file from disk, clones a voice, synthesises a
/// short phrase with the new voice, and finally deletes the custom voice.
const std = @import("std");
const typecast = @import("typecast");

pub fn main() !void {
    var gpa = std.heap.GeneralPurposeAllocator(.{}){};
    defer _ = gpa.deinit();
    const allocator = gpa.allocator();

    // ── Configuration ────────────────────────────────────────────────────
    const api_key = std.posix.getenv("TYPECAST_API_KEY") orelse {
        std.debug.print("Error: TYPECAST_API_KEY environment variable not set.\n", .{});
        std.process.exit(1);
    };

    const base_url = std.posix.getenv("TYPECAST_API_HOST") orelse "https://api.typecast.ai";

    // Audio file to clone from.  You can replace this with any WAV/MP3 ≤ 25 MB.
    const audio_path = std.posix.getenv("CLONE_AUDIO_PATH") orelse "sample.wav";

    // ── Load audio ───────────────────────────────────────────────────────
    const audio_file = std.fs.cwd().openFile(audio_path, .{}) catch |err| {
        std.debug.print("Error: cannot open audio file '{s}': {}\n", .{ audio_path, err });
        std.process.exit(1);
    };
    defer audio_file.close();

    const audio_bytes = try audio_file.readToEndAlloc(allocator, typecast.CLONING_MAX_FILE_SIZE);
    defer allocator.free(audio_bytes);

    std.debug.print("Loaded audio: {s} ({d} bytes)\n", .{ audio_path, audio_bytes.len });

    // ── Create client ────────────────────────────────────────────────────
    var client = typecast.Client.init(allocator, .{
        .api_key = api_key,
        .base_url = base_url,
    });
    defer client.deinit();

    // ── Clone voice ──────────────────────────────────────────────────────
    std.debug.print("Cloning voice...\n", .{});

    const custom_voice = try client.cloneVoice(
        allocator,
        audio_bytes,
        audio_path,     // filename — used for MIME type detection
        "My Zig Voice", // display name (1–30 chars)
        "ssfm-v30",     // model
    );
    defer {
        allocator.free(custom_voice.voice_id);
        allocator.free(custom_voice.name);
        allocator.free(custom_voice.model);
    }

    std.debug.print("Cloned voice: id={s}  name={s}  model={s}\n", .{
        custom_voice.voice_id,
        custom_voice.name,
        custom_voice.model,
    });

    // ── Synthesise with the new voice ────────────────────────────────────
    std.debug.print("Synthesising with the custom voice...\n", .{});

    const tts_response = try client.textToSpeech(.{
        .voice_id = custom_voice.voice_id,
        .text = "Hello! This audio was created with my cloned Zig voice.",
        .model = .ssfm_v30,
    });
    defer allocator.free(tts_response.audio_data);

    const out_path = "cloned_output.wav";
    const out_file = try std.fs.cwd().createFile(out_path, .{});
    defer out_file.close();
    try out_file.writeAll(tts_response.audio_data);

    std.debug.print("Saved {s} ({d} bytes, {d:.2}s)\n", .{
        out_path,
        tts_response.audio_data.len,
        tts_response.duration,
    });

    // ── Delete the custom voice ──────────────────────────────────────────
    std.debug.print("Deleting custom voice {s}...\n", .{custom_voice.voice_id});
    try client.deleteVoice(custom_voice.voice_id);
    std.debug.print("Done.\n", .{});
}
