const std = @import("std");
const typecast = @import("typecast");
const Client = typecast.Client;
const models = typecast.models;

// ── Mock Server ──────────────────────────────────────────────────────

const MockServer = struct {
    server: std.net.Server,
    thread: ?std.Thread = null,
    response_status: u16 = 200,
    response_body: []const u8 = "",
    response_content_type: []const u8 = "application/octet-stream",
    extra_headers: []const u8 = "",
    use_chunked: bool = false,
    captured: [8192]u8 = undefined,
    captured_len: usize = 0,

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

        // Read request (drain headers + body so client doesn't get broken pipe).
        // Accumulate into a buffer and search the full accumulated data
        // for the header terminator, since \r\n\r\n may span two reads.
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
        self.captured_len = @min(total, self.captured.len);
        @memcpy(self.captured[0..self.captured_len], buf[0..self.captured_len]);

        // Drain request body based on Content-Length header.
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

        // Build HTTP response
        const status_text = statusText(self.response_status);
        var resp_buf: [65536]u8 = undefined;
        var stream = std.io.fixedBufferStream(&resp_buf);
        const writer = stream.writer();

        if (self.use_chunked) {
            // Chunked transfer encoding — exercises the streaming read path.
            writer.print(
                "HTTP/1.1 {d} {s}\r\nTransfer-Encoding: chunked\r\nContent-Type: {s}\r\nConnection: close\r\n{s}\r\n",
                .{
                    self.response_status,
                    status_text,
                    self.response_content_type,
                    self.extra_headers,
                },
            ) catch return;
            conn.stream.writeAll(stream.getWritten()) catch return;
            // Send body as a single chunk: <hex-size>\r\n<data>\r\n0\r\n\r\n
            var chunk_header_buf: [32]u8 = undefined;
            var chunk_stream = std.io.fixedBufferStream(&chunk_header_buf);
            chunk_stream.writer().print("{x}\r\n", .{self.response_body.len}) catch return;
            conn.stream.writeAll(chunk_header_buf[0..chunk_stream.pos]) catch return;
            conn.stream.writeAll(self.response_body) catch return;
            conn.stream.writeAll("\r\n0\r\n\r\n") catch return;
        } else {
            writer.print(
                "HTTP/1.1 {d} {s}\r\nContent-Length: {d}\r\nContent-Type: {s}\r\nConnection: close\r\n{s}\r\n",
                .{
                    self.response_status,
                    status_text,
                    self.response_body.len,
                    self.response_content_type,
                    self.extra_headers,
                },
            ) catch return;
            conn.stream.writeAll(stream.getWritten()) catch return;
            conn.stream.writeAll(self.response_body) catch return;
        }
    }

    fn deinit(self: *MockServer) void {
        self.server.deinit();
        if (self.thread) |t| t.join();
    }

    fn capturedRequest(self: *const MockServer) []const u8 {
        return self.captured[0..self.captured_len];
    }

    fn statusText(code: u16) []const u8 {
        return switch (code) {
            200 => "OK",
            400 => "Bad Request",
            401 => "Unauthorized",
            402 => "Payment Required",
            404 => "Not Found",
            422 => "Unprocessable Entity",
            429 => "Too Many Requests",
            500 => "Internal Server Error",
            else => "Unknown",
        };
    }
};

fn buildBaseUrl(port: u16) ![64]u8 {
    var buf: [64]u8 = undefined;
    var stream = std.io.fixedBufferStream(&buf);
    try stream.writer().print("http://127.0.0.1:{d}", .{port});
    // Pad the rest with zeros so we can slice it
    const written = stream.pos;
    _ = written;
    return buf;
}

fn baseUrlSlice(buf: *[64]u8, port: u16) ![]const u8 {
    var stream = std.io.fixedBufferStream(buf);
    try stream.writer().print("http://127.0.0.1:{d}", .{port});
    return buf[0..stream.pos];
}

// ── Happy-path tests ─────────────────────────────────────────────────

test "textToSpeech returns audio data" {
    var mock = try MockServer.init();
    const audio_bytes = "RIFF\x00\x00\x00\x00WAVEfmt ";
    mock.response_status = 200;
    mock.response_body = audio_bytes;
    mock.response_content_type = "audio/wav";
    mock.extra_headers = "X-Audio-Duration: 2.5\r\n";
    try mock.start();
    defer mock.deinit();

    var url_buf: [64]u8 = undefined;
    const base_url = try baseUrlSlice(&url_buf, mock.getPort());

    var client = Client.init(std.testing.allocator, .{
        .api_key = "test-key",
        .base_url = base_url,
    });
    defer client.deinit();

    const response = try client.textToSpeech(.{
        .text = "Hello",
        .voice_id = "v1",
        .model = .ssfm_v21,
    });
    defer std.testing.allocator.free(response.audio_data);

    try std.testing.expectEqualStrings(audio_bytes, response.audio_data);
    try std.testing.expectApproxEqAbs(2.5, response.duration, 0.01);
    try std.testing.expectEqual(models.AudioFormat.wav, response.format);
}

test "generateToFile validates path and infers uppercase mp3 extension" {
    var url_buf: [64]u8 = undefined;
    var client = Client.init(std.testing.allocator, .{
        .api_key = "test-key",
        .base_url = try baseUrlSlice(&url_buf, 1),
    });
    defer client.deinit();

    try std.testing.expectError(error.InvalidPath, client.generateToFile("   ", .{
        .text = "Hello",
        .voice_id = "v1",
    }));

    var mock = try MockServer.init();
    mock.response_status = 200;
    mock.response_body = "MP3";
    mock.response_content_type = "audio/mp3";
    try mock.start();
    defer mock.deinit();

    const base_url = try baseUrlSlice(&url_buf, mock.getPort());
    client.base_url = base_url;

    var path_buf: [128]u8 = undefined;
    const path = try std.fmt.bufPrint(
        &path_buf,
        "/tmp/typecast-zig-{d}.MP3",
        .{std.process.getpid()},
    );
    std.fs.cwd().deleteFile(path) catch {};
    defer std.fs.cwd().deleteFile(path) catch {};

    const response = try client.generateToFile(path, .{
        .text = "Hello",
        .voice_id = "v1",
    });
    defer std.testing.allocator.free(response.audio_data);

    const written = try std.fs.cwd().readFileAlloc(std.testing.allocator, path, 16);
    defer std.testing.allocator.free(written);
    try std.testing.expectEqualStrings("MP3", written);
    const captured = mock.capturedRequest();
    try std.testing.expect(std.mem.indexOf(u8, captured, "\"audio_format\":\"mp3\"") != null);
}

test "textToSpeechStream calls callback with chunks" {
    var mock = try MockServer.init();
    mock.response_status = 200;
    mock.response_body = "audio-data-here";
    mock.response_content_type = "audio/wav";
    mock.use_chunked = true;
    try mock.start();
    defer mock.deinit();

    var url_buf: [64]u8 = undefined;
    const base_url = try baseUrlSlice(&url_buf, mock.getPort());

    var client = Client.init(std.testing.allocator, .{
        .api_key = "test-key",
        .base_url = base_url,
    });
    defer client.deinit();

    const Ctx = struct {
        fn callback(chunk: []const u8) anyerror!void {
            cb_total += chunk.len;
        }
        var cb_total: usize = 0;
    };
    Ctx.cb_total = 0;

    try client.textToSpeechStream(
        .{
            .text = "Hello",
            .voice_id = "v1",
            .model = .ssfm_v21,
        },
        &Ctx.callback,
    );

    // Assert the callback was actually invoked with data.
    try std.testing.expect(Ctx.cb_total > 0);
}

test "getMySubscription parses response" {
    var mock = try MockServer.init();
    mock.response_status = 200;
    mock.response_body =
        \\{"plan":"plus","credits":{"plan_credits":100000,"used_credits":1234},"limits":{"concurrency_limit":5}}
    ;
    mock.response_content_type = "application/json";
    try mock.start();
    defer mock.deinit();

    var url_buf: [64]u8 = undefined;
    const base_url = try baseUrlSlice(&url_buf, mock.getPort());

    var client = Client.init(std.testing.allocator, .{
        .api_key = "test-key",
        .base_url = base_url,
    });
    defer client.deinit();

    const sub = try client.getMySubscription();
    defer std.testing.allocator.free(sub.plan);

    try std.testing.expectEqualStrings("plus", sub.plan);
    try std.testing.expectEqual(@as(i64, 100000), sub.credits.plan_credits);
    try std.testing.expectEqual(@as(i64, 1234), sub.credits.used_credits);
    try std.testing.expectEqual(@as(i32, 5), sub.limits.concurrency_limit);
}

test "getVoices returns voice list" {
    var mock = try MockServer.init();
    mock.response_status = 200;
    mock.response_body =
        \\[{"voice_id":"v1","voice_name":"Alice","model":"ssfm-v21","emotions":["happy","sad"]},{"voice_id":"v2","voice_name":"Bob","model":"ssfm-v30","emotions":["normal"]}]
    ;
    mock.response_content_type = "application/json";
    try mock.start();
    defer mock.deinit();

    var url_buf: [64]u8 = undefined;
    const base_url = try baseUrlSlice(&url_buf, mock.getPort());

    var client = Client.init(std.testing.allocator, .{
        .api_key = "test-key",
        .base_url = base_url,
    });
    defer client.deinit();

    const voices = try client.getVoices(null);
    defer {
        for (voices) |v| {
            std.testing.allocator.free(v.voice_id);
            std.testing.allocator.free(v.voice_name);
            for (v.emotions) |e| std.testing.allocator.free(e);
            std.testing.allocator.free(v.emotions);
        }
        std.testing.allocator.free(voices);
    }

    try std.testing.expectEqual(@as(usize, 2), voices.len);
    try std.testing.expectEqualStrings("v1", voices[0].voice_id);
    try std.testing.expectEqualStrings("Alice", voices[0].voice_name);
    try std.testing.expectEqual(models.TtsModel.ssfm_v21, voices[0].model);
    try std.testing.expectEqual(@as(usize, 2), voices[0].emotions.len);
    try std.testing.expectEqualStrings("v2", voices[1].voice_id);
    try std.testing.expectEqual(models.TtsModel.ssfm_v30, voices[1].model);
}

test "getVoicesV2 returns voice list" {
    var mock = try MockServer.init();
    mock.response_status = 200;
    mock.response_body =
        \\[{"voice_id":"v1","voice_name":"Alice","models":[{"version":"ssfm-v21","emotions":["happy"]}],"gender":"female","age":"young_adult","use_cases":["narration"]}]
    ;
    mock.response_content_type = "application/json";
    try mock.start();
    defer mock.deinit();

    var url_buf: [64]u8 = undefined;
    const base_url = try baseUrlSlice(&url_buf, mock.getPort());

    var client = Client.init(std.testing.allocator, .{
        .api_key = "test-key",
        .base_url = base_url,
    });
    defer client.deinit();

    const voices = try client.getVoicesV2(null);
    defer {
        for (voices) |v| {
            std.testing.allocator.free(v.voice_id);
            std.testing.allocator.free(v.voice_name);
            for (v.models) |m| {
                std.testing.allocator.free(m.version);
                for (m.emotions) |e| std.testing.allocator.free(e);
                std.testing.allocator.free(m.emotions);
            }
            std.testing.allocator.free(v.models);
            if (v.use_cases) |uc| {
                for (uc) |c| std.testing.allocator.free(c);
                std.testing.allocator.free(uc);
            }
        }
        std.testing.allocator.free(voices);
    }

    try std.testing.expectEqual(@as(usize, 1), voices.len);
    try std.testing.expectEqualStrings("v1", voices[0].voice_id);
    try std.testing.expectEqualStrings("Alice", voices[0].voice_name);
    try std.testing.expectEqual(models.Gender.female, voices[0].gender.?);
    try std.testing.expectEqual(models.Age.young_adult, voices[0].age.?);
    try std.testing.expectEqual(@as(usize, 1), voices[0].use_cases.?.len);
}

test "getVoiceV2 returns single voice" {
    var mock = try MockServer.init();
    mock.response_status = 200;
    mock.response_body =
        \\[{"voice_id":"v1","voice_name":"Alice","models":[{"version":"ssfm-v30","emotions":["normal","happy"]}],"gender":"female","age":"young_adult"}]
    ;
    mock.response_content_type = "application/json";
    try mock.start();
    defer mock.deinit();

    var url_buf: [64]u8 = undefined;
    const base_url = try baseUrlSlice(&url_buf, mock.getPort());

    var client = Client.init(std.testing.allocator, .{
        .api_key = "test-key",
        .base_url = base_url,
    });
    defer client.deinit();

    const voice = try client.getVoiceV2("v1", null);
    defer {
        std.testing.allocator.free(voice.voice_id);
        std.testing.allocator.free(voice.voice_name);
        for (voice.models) |m| {
            std.testing.allocator.free(m.version);
            for (m.emotions) |e| std.testing.allocator.free(e);
            std.testing.allocator.free(m.emotions);
        }
        std.testing.allocator.free(voice.models);
    }

    try std.testing.expectEqualStrings("v1", voice.voice_id);
    try std.testing.expectEqualStrings("Alice", voice.voice_name);
    try std.testing.expectEqual(@as(usize, 1), voice.models.len);
}

// ── Error-path tests ─────────────────────────────────────────────────

fn expectHttpError(comptime expected_error: anytype, port: u16) !void {
    var url_buf: [64]u8 = undefined;
    const base_url = try baseUrlSlice(&url_buf, port);

    var client = Client.init(std.testing.allocator, .{
        .api_key = "test-key",
        .base_url = base_url,
    });
    defer client.deinit();

    const result = client.getMySubscription();
    try std.testing.expectError(expected_error, result);
}

test "HTTP 400 returns error.BadRequest" {
    var mock = try MockServer.init();
    mock.response_status = 400;
    mock.response_body = "{}";
    mock.response_content_type = "application/json";
    try mock.start();
    defer mock.deinit();
    try expectHttpError(error.BadRequest, mock.getPort());
}

test "HTTP 401 returns error.Unauthorized" {
    var mock = try MockServer.init();
    mock.response_status = 401;
    mock.response_body = "{}";
    mock.response_content_type = "application/json";
    try mock.start();
    defer mock.deinit();
    try expectHttpError(error.Unauthorized, mock.getPort());
}

test "HTTP 402 returns error.PaymentRequired" {
    var mock = try MockServer.init();
    mock.response_status = 402;
    mock.response_body = "{}";
    mock.response_content_type = "application/json";
    try mock.start();
    defer mock.deinit();
    try expectHttpError(error.PaymentRequired, mock.getPort());
}

test "HTTP 404 returns error.NotFound" {
    var mock = try MockServer.init();
    mock.response_status = 404;
    mock.response_body = "{}";
    mock.response_content_type = "application/json";
    try mock.start();
    defer mock.deinit();
    try expectHttpError(error.NotFound, mock.getPort());
}

test "HTTP 422 returns error.UnprocessableEntity" {
    var mock = try MockServer.init();
    mock.response_status = 422;
    mock.response_body = "{}";
    mock.response_content_type = "application/json";
    try mock.start();
    defer mock.deinit();
    try expectHttpError(error.UnprocessableEntity, mock.getPort());
}

test "HTTP 429 returns error.RateLimited" {
    var mock = try MockServer.init();
    mock.response_status = 429;
    mock.response_body = "{}";
    mock.response_content_type = "application/json";
    try mock.start();
    defer mock.deinit();
    try expectHttpError(error.RateLimited, mock.getPort());
}

test "HTTP 500 returns error.InternalServerError" {
    var mock = try MockServer.init();
    mock.response_status = 500;
    mock.response_body = "{}";
    mock.response_content_type = "application/json";
    try mock.start();
    defer mock.deinit();
    try expectHttpError(error.InternalServerError, mock.getPort());
}
