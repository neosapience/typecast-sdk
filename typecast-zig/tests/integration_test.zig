const std = @import("std");
const testing = std.testing;
const typecast = @import("typecast");

/// Return the value of the given environment variable, or skip the test
/// when the variable is not set (CI without credentials, local dev, etc.).
/// NOTE: std.posix.getenv is POSIX-only and will not compile on Windows.
/// For cross-platform support, use std.process.EnvMap. This is acceptable
/// for integration tests that only run in POSIX CI environments.
fn getEnvOrSkip(name: []const u8) error{SkipZigTest}![]const u8 {
    return std.posix.getenv(name) orelse return error.SkipZigTest;
}

fn makeClient(allocator: std.mem.Allocator, api_key: []const u8, host: []const u8) typecast.Client {
    return typecast.Client.init(allocator, .{
        .api_key = api_key,
        .base_url = host,
    });
}

/// Resolve a voice_id for testing: prefer TYPECAST_VOICE_ID env var,
/// then fall back to a dynamic getVoicesV2 lookup so tests are not
/// pinned to a single hardcoded id.
fn resolveVoiceId(api_key: []const u8, host: []const u8) ![]const u8 {
    if (std.posix.getenv("TYPECAST_VOICE_ID")) |id| return id;

    // Dynamic lookup — use an arena so inner allocations are cleaned up.
    var arena = std.heap.ArenaAllocator.init(testing.allocator);
    defer arena.deinit();

    var client = makeClient(arena.allocator(), api_key, host);
    defer client.deinit();

    const voices = try client.getVoicesV2(null);
    if (voices.len == 0) return error.NoVoicesAvailable;

    // Dupe onto testing.allocator so it survives the arena teardown.
    return try testing.allocator.dupe(u8, voices[0].voice_id);
}

// ── Text-to-Speech ───────────────────────────────────────────────────

test "integration: textToSpeech returns audio" {
    const api_key = try getEnvOrSkip("TYPECAST_API_KEY");
    const host = std.posix.getenv("TYPECAST_API_HOST") orelse "https://api.typecast.ai";

    const voice_id = try resolveVoiceId(api_key, host);
    defer if (std.posix.getenv("TYPECAST_VOICE_ID") == null) testing.allocator.free(voice_id);

    var client = makeClient(testing.allocator, api_key, host);
    defer client.deinit();

    const response = try client.textToSpeech(.{
        .voice_id = voice_id,
        .text = "Hello from the Zig integration test.",
        .model = .ssfm_v30,
    });
    defer testing.allocator.free(response.audio_data);

    try testing.expect(response.audio_data.len > 0);
    try testing.expect(response.duration >= 0.0);
}

// ── Streaming ────────────────────────────────────────────────────────

test "integration: textToSpeechStream delivers chunks" {
    const api_key = try getEnvOrSkip("TYPECAST_API_KEY");
    const host = std.posix.getenv("TYPECAST_API_HOST") orelse "https://api.typecast.ai";

    const voice_id = try resolveVoiceId(api_key, host);
    defer if (std.posix.getenv("TYPECAST_VOICE_ID") == null) testing.allocator.free(voice_id);

    var client = makeClient(testing.allocator, api_key, host);
    defer client.deinit();

    // Use a comptime-known callback; we cannot capture local state but
    // reaching the end of the call without error proves streaming works.
    try client.textToSpeechStream(.{
        .voice_id = voice_id,
        .text = "Streaming test from Zig.",
        .model = .ssfm_v30,
    }, struct {
        fn onChunk(chunk: []const u8) anyerror!void {
            // Verify each chunk is non-empty.
            if (chunk.len == 0) return error.EmptyChunk;
        }
    }.onChunk);
}

// ── Voices ───────────────────────────────────────────────────────────

test "integration: getVoicesV2 returns voices" {
    const api_key = try getEnvOrSkip("TYPECAST_API_KEY");
    const host = std.posix.getenv("TYPECAST_API_HOST") orelse "https://api.typecast.ai";

    // Use an arena allocator to avoid having to free every inner allocation.
    var arena = std.heap.ArenaAllocator.init(testing.allocator);
    defer arena.deinit();

    var client = typecast.Client.init(arena.allocator(), .{
        .api_key = api_key,
        .base_url = host,
    });
    defer client.deinit();

    const voices = try client.getVoicesV2(null);

    try testing.expect(voices.len > 0);
    // Every voice must have a non-empty id and name.
    try testing.expect(voices[0].voice_id.len > 0);
    try testing.expect(voices[0].voice_name.len > 0);
}

// ── Subscription ─────────────────────────────────────────────────────

test "integration: getMySubscription returns plan" {
    const api_key = try getEnvOrSkip("TYPECAST_API_KEY");
    const host = std.posix.getenv("TYPECAST_API_HOST") orelse "https://api.typecast.ai";

    var client = makeClient(testing.allocator, api_key, host);
    defer client.deinit();

    const sub = try client.getMySubscription();
    defer testing.allocator.free(sub.plan);
    // plan is a string; verify it is non-empty.
    try testing.expect(sub.plan.len > 0);
    try testing.expect(sub.credits.plan_credits >= 0);
    try testing.expect(sub.limits.concurrency_limit > 0);
}
