const std = @import("std");
const testing = std.testing;
const typecast = @import("typecast");
const ts = typecast.timestamps;

// Fixture paths relative to the typecast-zig/ directory (the build root).
// Tests are run with cwd set to the build root by `zig build`.
const FIXTURE_DIR = "../test-fixtures/with-timestamps";
const EXPECTED_DIR = FIXTURE_DIR ++ "/expected";

// ── Helper: load a file into a heap slice ──────────────────────────────

fn loadFile(allocator: std.mem.Allocator, path: []const u8) ![]u8 {
    const file = std.fs.cwd().openFile(path, .{}) catch |err| {
        std.debug.print("Failed to open '{s}': {}\n", .{ path, err });
        return err;
    };
    defer file.close();
    return file.readToEndAlloc(allocator, 8 * 1024 * 1024);
}

// ── Fixture runner ─────────────────────────────────────────────────────

fn checkFixture(name: []const u8) !void {
    const allocator = testing.allocator;

    // Build fixture path: FIXTURE_DIR/<name>.json
    const json_name = try std.mem.concat(allocator, u8, &.{ name, ".json" });
    defer allocator.free(json_name);
    const json_path = try std.fs.path.join(allocator, &.{ FIXTURE_DIR, json_name });
    defer allocator.free(json_path);

    // Build expected SRT path
    const srt_name = try std.mem.concat(allocator, u8, &.{ name, ".srt" });
    defer allocator.free(srt_name);
    const srt_path = try std.fs.path.join(allocator, &.{ EXPECTED_DIR, srt_name });
    defer allocator.free(srt_path);

    // Build expected VTT path
    const vtt_name = try std.mem.concat(allocator, u8, &.{ name, ".vtt" });
    defer allocator.free(vtt_name);
    const vtt_path = try std.fs.path.join(allocator, &.{ EXPECTED_DIR, vtt_name });
    defer allocator.free(vtt_path);

    // Load and parse fixture JSON.
    const json_data = try loadFile(allocator, json_path);
    defer allocator.free(json_data);

    var resp = try ts.parseWithTimestampsResponse(allocator, json_data);
    defer resp.deinit();

    // Generate and compare SRT.
    const srt = try resp.toSrt(allocator);
    defer allocator.free(srt);

    const expected_srt = try loadFile(allocator, srt_path);
    defer allocator.free(expected_srt);

    if (!std.mem.eql(u8, expected_srt, srt)) {
        std.debug.print("\n=== SRT MISMATCH for '{s}' ===\n--- expected ---\n{s}\n--- got ---\n{s}\n", .{ name, expected_srt, srt });
        return error.SrtMismatch;
    }

    // Generate and compare VTT.
    const vtt = try resp.toVtt(allocator);
    defer allocator.free(vtt);

    const expected_vtt = try loadFile(allocator, vtt_path);
    defer allocator.free(expected_vtt);

    if (!std.mem.eql(u8, expected_vtt, vtt)) {
        std.debug.print("\n=== VTT MISMATCH for '{s}' ===\n--- expected ---\n{s}\n--- got ---\n{s}\n", .{ name, expected_vtt, vtt });
        return error.VttMismatch;
    }
}

// ── Fixture tests ──────────────────────────────────────────────────────

test "fixture both: SRT and VTT byte-for-byte match" {
    try checkFixture("both");
}

test "fixture word_only: SRT and VTT byte-for-byte match" {
    try checkFixture("word_only");
}

test "fixture char_only: SRT and VTT byte-for-byte match" {
    try checkFixture("char_only");
}

test "fixture jpn_char: SRT and VTT byte-for-byte match" {
    try checkFixture("jpn_char");
}

// ── Unit tests ─────────────────────────────────────────────────────────

test "toSrt single English sentence (word mode)" {
    const allocator = testing.allocator;
    var words = [_]ts.AlignmentSegmentWord{
        .{ .text = "Hello.", .start = 0.1, .end = 0.5 },
        .{ .text = "World.", .start = 0.6, .end = 1.0 },
    };
    const resp = ts.TTSWithTimestampsResponse{
        .audio_base64 = "",
        .audio_format = "wav",
        .audio_duration = 2.5,
        .words = &words,
        .characters = null,
        .allocator = allocator,
    };
    const srt = try resp.toSrt(allocator);
    defer allocator.free(srt);

    // Two cues because each word ends with '.'.
    try testing.expect(std.mem.indexOf(u8, srt, "Hello.") != null);
    try testing.expect(std.mem.indexOf(u8, srt, "World.") != null);
    // SRT timestamp separator uses comma.
    try testing.expect(std.mem.indexOf(u8, srt, "00:00:00,100 --> 00:00:00,500") != null);
}

test "toVtt header present and uses dot separator" {
    const allocator = testing.allocator;
    var words = [_]ts.AlignmentSegmentWord{
        .{ .text = "Hi!", .start = 0.0, .end = 0.5 },
    };
    const resp = ts.TTSWithTimestampsResponse{
        .audio_base64 = "",
        .audio_format = "wav",
        .audio_duration = 1.0,
        .words = &words,
        .characters = null,
        .allocator = allocator,
    };
    const vtt = try resp.toVtt(allocator);
    defer allocator.free(vtt);

    try testing.expect(std.mem.startsWith(u8, vtt, "WEBVTT\n\n"));
    // VTT timestamp format uses '.' not ','.
    try testing.expect(std.mem.indexOf(u8, vtt, "00:00:00.000 --> 00:00:00.500") != null);
}

test "falls back to characters when words is null" {
    const allocator = testing.allocator;
    var chars = [_]ts.AlignmentSegmentCharacter{
        .{ .text = "A", .start = 0.0, .end = 0.2 },
        .{ .text = "B", .start = 0.2, .end = 0.4 },
        .{ .text = ".", .start = 0.4, .end = 0.5 },
    };
    const resp = ts.TTSWithTimestampsResponse{
        .audio_base64 = "",
        .audio_format = "wav",
        .audio_duration = 1.0,
        .words = null,
        .characters = &chars,
        .allocator = allocator,
    };
    const srt = try resp.toSrt(allocator);
    defer allocator.free(srt);

    // Characters concatenated without spaces.
    try testing.expect(std.mem.indexOf(u8, srt, "AB.") != null);
}

test "serializeTtsRequestWithTimestamps includes granularity" {
    const allocator = testing.allocator;
    const req = ts.TTSRequestWithTimestamps{
        .voice_id = "v1",
        .text = "Hello",
        .model = .ssfm_v30,
    };
    const json = try ts.serializeTtsRequestWithTimestamps(allocator, req, "word");
    defer allocator.free(json);

    const parsed = try std.json.parseFromSlice(std.json.Value, allocator, json, .{});
    defer parsed.deinit();
    const obj = parsed.value.object;

    try testing.expectEqualStrings("word", obj.get("granularity").?.string);
    try testing.expectEqualStrings("Hello", obj.get("text").?.string);
}

test "serializeTtsRequestWithTimestamps null granularity omits field" {
    const allocator = testing.allocator;
    const req = ts.TTSRequestWithTimestamps{
        .voice_id = "v1",
        .text = "Hello",
        .model = .ssfm_v30,
    };
    const json = try ts.serializeTtsRequestWithTimestamps(allocator, req, null);
    defer allocator.free(json);

    const parsed = try std.json.parseFromSlice(std.json.Value, allocator, json, .{});
    defer parsed.deinit();
    const obj = parsed.value.object;

    try testing.expect(obj.get("granularity") == null);
}

test "parseWithTimestampsResponse parses words and characters" {
    const allocator = testing.allocator;
    const json_str =
        \\{"audio":"","audio_format":"wav","audio_duration":1.5,
        \\"words":[{"text":"Hello.","start":0.1,"end":0.5}],
        \\"characters":[{"text":"H","start":0.1,"end":0.2},{"text":"i","start":0.2,"end":0.3}]}
    ;
    var resp = try ts.parseWithTimestampsResponse(allocator, json_str);
    defer resp.deinit();

    try testing.expectEqualStrings("wav", resp.audio_format);
    try testing.expectApproxEqAbs(@as(f64, 1.5), resp.audio_duration, 0.001);
    try testing.expectEqual(@as(usize, 1), resp.words.?.len);
    try testing.expectEqualStrings("Hello.", resp.words.?[0].text);
    try testing.expectEqual(@as(usize, 2), resp.characters.?.len);
}

// ── Mock HTTP test ─────────────────────────────────────────────────────

const Client = typecast.Client;

const MockServer = struct {
    server: std.net.Server,
    thread: ?std.Thread = null,
    response_body: []const u8 = "",

    fn init() !MockServer {
        const address = std.net.Address.initIp4(.{ 127, 0, 0, 1 }, 0);
        const server = try address.listen(.{ .reuse_address = true });
        return .{ .server = server };
    }

    fn getPort(self: *MockServer) u16 {
        return self.server.listen_address.getPort();
    }

    fn start(self: *MockServer) !void {
        self.thread = try std.Thread.spawn(.{}, serverLoop, .{self});
    }

    fn serverLoop(self: *MockServer) void {
        const conn = self.server.accept() catch return;
        defer conn.stream.close();

        // Drain the request.
        var buf: [8192]u8 = undefined;
        var total: usize = 0;
        var header_end: ?usize = null;
        while (total < buf.len) {
            const n = conn.stream.read(buf[total..]) catch break;
            if (n == 0) break;
            total += n;
            if (header_end == null) {
                if (std.mem.indexOf(u8, buf[0..total], "\r\n\r\n")) |pos| {
                    header_end = pos + 4;
                }
            }
            if (header_end != null) break;
        }
        if (header_end) |hend| {
            const headers = buf[0..hend];
            var content_length: usize = 0;
            var it = std.mem.splitSequence(u8, headers, "\r\n");
            while (it.next()) |line| {
                if (std.ascii.startsWithIgnoreCase(line, "content-length:")) {
                    const val = std.mem.trimLeft(u8, line["content-length:".len..], " ");
                    content_length = std.fmt.parseInt(usize, val, 10) catch 0;
                    break;
                }
            }
            if (content_length > 0) {
                const body_already = total - hend;
                var remaining = if (content_length > body_already) content_length - body_already else 0;
                while (remaining > 0) {
                    var drain_buf: [4096]u8 = undefined;
                    const to_read = @min(remaining, drain_buf.len);
                    const n = conn.stream.read(drain_buf[0..to_read]) catch break;
                    if (n == 0) break;
                    remaining -= n;
                }
            }
        }

        // Send response.
        var resp_buf: [65536]u8 = undefined;
        var stream = std.io.fixedBufferStream(&resp_buf);
        const writer = stream.writer();
        writer.print(
            "HTTP/1.1 200 OK\r\nContent-Length: {d}\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n",
            .{self.response_body.len},
        ) catch return;
        conn.stream.writeAll(stream.getWritten()) catch return;
        conn.stream.writeAll(self.response_body) catch return;
    }

    fn deinit(self: *MockServer) void {
        self.server.deinit();
        if (self.thread) |t| t.join();
    }
};

fn baseUrlSlice(buf: *[64]u8, port: u16) ![]const u8 {
    var stream = std.io.fixedBufferStream(buf);
    try stream.writer().print("http://127.0.0.1:{d}", .{port});
    return buf[0..stream.pos];
}

test "textToSpeechWithTimestamps mock HTTP round-trip" {
    var mock = try MockServer.init();
    // Minimal valid JSON response (audio is empty string = empty base64).
    mock.response_body =
        \\{"audio":"","audio_format":"wav","audio_duration":1.0,
        \\"words":[{"text":"Hello.","start":0.1,"end":0.5},{"text":"World.","start":0.6,"end":1.0}],
        \\"characters":null}
    ;
    try mock.start();
    defer mock.deinit();

    var url_buf: [64]u8 = undefined;
    const base_url = try baseUrlSlice(&url_buf, mock.getPort());

    var client = Client.init(testing.allocator, .{
        .api_key = "test-key",
        .base_url = base_url,
    });
    defer client.deinit();

    var resp = try client.textToSpeechWithTimestamps(
        .{
            .voice_id = "v1",
            .text = "Hello. World.",
            .model = .ssfm_v30,
        },
        null,
    );
    defer resp.deinit();

    try testing.expectEqualStrings("wav", resp.audio_format);
    try testing.expectApproxEqAbs(@as(f64, 1.0), resp.audio_duration, 0.001);
    try testing.expectEqual(@as(usize, 2), resp.words.?.len);
    try testing.expectEqualStrings("Hello.", resp.words.?[0].text);

    // Generate SRT and verify basic structure.
    const srt = try resp.toSrt(testing.allocator);
    defer testing.allocator.free(srt);
    try testing.expect(std.mem.indexOf(u8, srt, "Hello.") != null);
    try testing.expect(std.mem.indexOf(u8, srt, "World.") != null);
}
