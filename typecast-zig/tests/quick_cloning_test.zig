const std = @import("std");
const testing = std.testing;
const typecast = @import("typecast");
const Client = typecast.Client;
const models = typecast.models;

// ── Inspecting Mock Server ────────────────────────────────────────────
//
// This server variant captures the complete request (headers + body) so
// tests can assert on Content-Type and multipart body shape.

const MAX_REQUEST: usize = 256 * 1024; // 256 KB is enough for tests

const CapturedRequest = struct {
    method: []const u8,
    path: []const u8,
    headers: []const u8,
    body: []const u8,
    allocator: std.mem.Allocator,

    fn deinit(self: *CapturedRequest) void {
        self.allocator.free(self.method);
        self.allocator.free(self.path);
        self.allocator.free(self.headers);
        self.allocator.free(self.body);
    }
};

const InspectingMockServer = struct {
    server: std.net.Server,
    thread: ?std.Thread = null,
    response_status: u16 = 200,
    response_body: []const u8 = "",
    response_content_type: []const u8 = "application/json",
    // Filled after the thread finishes.
    captured: ?CapturedRequest = null,

    fn init() !InspectingMockServer {
        const address = std.net.Address.initIp4(.{ 127, 0, 0, 1 }, 0);
        const server = try address.listen(.{ .reuse_address = true });
        return .{ .server = server };
    }

    fn getPort(self: *InspectingMockServer) u16 {
        return self.server.listen_address.getPort();
    }

    fn start(self: *InspectingMockServer) !void {
        self.thread = try std.Thread.spawn(.{}, serverLoop, .{self});
    }

    fn serverLoop(self: *InspectingMockServer) void {
        const allocator = std.heap.page_allocator;

        const conn = self.server.accept() catch return;
        defer conn.stream.close();

        // Read all incoming bytes into a dynamically sized buffer.
        var raw: std.ArrayList(u8) = .empty;
        defer raw.deinit(allocator);
        var tmp: [8192]u8 = undefined;
        var header_end: ?usize = null;
        while (raw.items.len < MAX_REQUEST) {
            const n = conn.stream.read(&tmp) catch break;
            if (n == 0) break;
            raw.appendSlice(allocator, tmp[0..n]) catch break;
            if (header_end == null) {
                if (std.mem.indexOf(u8, raw.items, "\r\n\r\n")) |pos| {
                    header_end = pos + 4;
                }
            }
            // Once we know the header end, parse Content-Length and read exactly that many more bytes.
            if (header_end) |hend| {
                const header_section = raw.items[0..hend];
                var content_length: usize = 0;
                var it = std.mem.splitSequence(u8, header_section, "\r\n");
                while (it.next()) |line| {
                    if (std.ascii.startsWithIgnoreCase(line, "content-length:")) {
                        const val = std.mem.trimLeft(u8, line["content-length:".len..], " ");
                        content_length = std.fmt.parseInt(usize, val, 10) catch 0;
                        break;
                    }
                }
                const body_so_far = raw.items.len - hend;
                if (content_length == 0 or body_so_far >= content_length) break;
                // Need more body bytes.
                const remaining = content_length - body_so_far;
                var buf2: [8192]u8 = undefined;
                var left = remaining;
                while (left > 0) {
                    const chunk = @min(left, buf2.len);
                    const m = conn.stream.read(buf2[0..chunk]) catch break;
                    if (m == 0) break;
                    raw.appendSlice(allocator, buf2[0..m]) catch break;
                    left -= m;
                }
                break;
            }
        }

        // Parse request line + headers vs body.
        const hend = header_end orelse raw.items.len;
        const header_bytes = raw.items[0..hend];
        const body_bytes = if (raw.items.len > hend) raw.items[hend..] else "";

        // Extract method and path from the first line.
        var first_line: []const u8 = "";
        if (std.mem.indexOf(u8, header_bytes, "\r\n")) |nl| {
            first_line = header_bytes[0..nl];
        }
        var method: []const u8 = "";
        var path: []const u8 = "";
        var parts = std.mem.splitScalar(u8, first_line, ' ');
        if (parts.next()) |m| method = m;
        if (parts.next()) |p| path = p;

        self.captured = CapturedRequest{
            .method = allocator.dupe(u8, method) catch return,
            .path = allocator.dupe(u8, path) catch return,
            .headers = allocator.dupe(u8, header_bytes) catch return,
            .body = allocator.dupe(u8, body_bytes) catch return,
            .allocator = allocator,
        };

        // Send response.
        const status_text = switch (self.response_status) {
            200 => "OK",
            204 => "No Content",
            400 => "Bad Request",
            401 => "Unauthorized",
            404 => "Not Found",
            422 => "Unprocessable Entity",
            429 => "Too Many Requests",
            500 => "Internal Server Error",
            else => "Unknown",
        };
        var resp_buf: [65536]u8 = undefined;
        var stream = std.io.fixedBufferStream(&resp_buf);
        stream.writer().print(
            "HTTP/1.1 {d} {s}\r\nContent-Length: {d}\r\nContent-Type: {s}\r\nConnection: close\r\n\r\n",
            .{ self.response_status, status_text, self.response_body.len, self.response_content_type },
        ) catch return;
        conn.stream.writeAll(stream.getWritten()) catch return;
        conn.stream.writeAll(self.response_body) catch return;
    }

    fn deinit(self: *InspectingMockServer) void {
        self.server.deinit();
        if (self.thread) |t| t.join();
        if (self.captured) |*c| c.deinit();
    }
};

fn baseUrlSlice(buf: *[64]u8, port: u16) ![]const u8 {
    var stream = std.io.fixedBufferStream(buf);
    try stream.writer().print("http://127.0.0.1:{d}", .{port});
    return buf[0..stream.pos];
}

// ── cloneVoice — happy path ───────────────────────────────────────────

test "cloneVoice returns CustomVoice on 200" {
    var mock = try InspectingMockServer.init();
    mock.response_status = 200;
    mock.response_body =
        \\{"voice_id":"vc_abc123","name":"My Voice","model":"ssfm-v30"}
    ;
    mock.response_content_type = "application/json";
    try mock.start();
    defer mock.deinit();

    var url_buf: [64]u8 = undefined;
    const base_url = try baseUrlSlice(&url_buf, mock.getPort());

    var client = Client.init(testing.allocator, .{
        .api_key = "test-key",
        .base_url = base_url,
    });
    defer client.deinit();

    const fake_audio = "RIFF\x24\x00\x00\x00WAVEfmt ";
    const result = try client.cloneVoice(testing.allocator, fake_audio, "sample.wav", "My Voice", "ssfm-v30");
    defer {
        testing.allocator.free(result.voice_id);
        testing.allocator.free(result.name);
        testing.allocator.free(result.model);
    }

    try testing.expectEqualStrings("vc_abc123", result.voice_id);
    try testing.expectEqualStrings("My Voice", result.name);
    try testing.expectEqualStrings("ssfm-v30", result.model);
}

// ── cloneVoice — multipart shape ─────────────────────────────────────

test "cloneVoice sends multipart body with name, model, and file parts" {
    var mock = try InspectingMockServer.init();
    mock.response_status = 200;
    mock.response_body =
        \\{"voice_id":"vc_def456","name":"Narrator","model":"ssfm-v21"}
    ;
    mock.response_content_type = "application/json";
    try mock.start();
    defer mock.deinit();

    var url_buf: [64]u8 = undefined;
    const base_url = try baseUrlSlice(&url_buf, mock.getPort());

    var client = Client.init(testing.allocator, .{
        .api_key = "test-key",
        .base_url = base_url,
    });
    defer client.deinit();

    const fake_audio = "fakemp3data";
    const result = try client.cloneVoice(testing.allocator, fake_audio, "voice.mp3", "Narrator", "ssfm-v21");
    defer {
        testing.allocator.free(result.voice_id);
        testing.allocator.free(result.name);
        testing.allocator.free(result.model);
    }

    // Wait for server thread then inspect the captured request.
    const cap = mock.captured orelse return error.TestNoCapturedRequest;

    // Content-Type header must include multipart/form-data and boundary.
    try testing.expect(std.mem.indexOf(u8, cap.headers, "multipart/form-data") != null);
    try testing.expect(std.mem.indexOf(u8, cap.headers, "boundary=") != null);

    // Body must contain each field name as a form-data part.
    try testing.expect(std.mem.indexOf(u8, cap.body, "name=\"name\"") != null);
    try testing.expect(std.mem.indexOf(u8, cap.body, "name=\"model\"") != null);
    try testing.expect(std.mem.indexOf(u8, cap.body, "name=\"file\"") != null);

    // Body must contain the actual field values.
    try testing.expect(std.mem.indexOf(u8, cap.body, "Narrator") != null);
    try testing.expect(std.mem.indexOf(u8, cap.body, "ssfm-v21") != null);

    // MIME type for .mp3 file.
    try testing.expect(std.mem.indexOf(u8, cap.body, "audio/mpeg") != null);

    // Raw audio bytes must appear verbatim in the body.
    try testing.expect(std.mem.indexOf(u8, cap.body, fake_audio) != null);
}

// ── cloneVoice — validation errors (no HTTP call needed) ─────────────

test "cloneVoice rejects audio that exceeds CLONING_MAX_FILE_SIZE" {
    // Allocate a slice just over the limit without actually populating it.
    const oversized = try testing.allocator.alloc(u8, models.CLONING_MAX_FILE_SIZE + 1);
    defer testing.allocator.free(oversized);
    @memset(oversized, 0);

    // Use a dummy base URL — the error should be raised before any HTTP call.
    var client = Client.init(testing.allocator, .{
        .api_key = "test-key",
        .base_url = "http://127.0.0.1:1", // nothing is listening here
    });
    defer client.deinit();

    const result = client.cloneVoice(testing.allocator, oversized, "big.wav", "Voice", "ssfm-v30");
    try testing.expectError(error.AudioTooLarge, result);
}

test "cloneVoice rejects name that is too short (empty string)" {
    var client = Client.init(testing.allocator, .{
        .api_key = "test-key",
        .base_url = "http://127.0.0.1:1",
    });
    defer client.deinit();

    const result = client.cloneVoice(testing.allocator, "audio", "sample.wav", "", "ssfm-v30");
    try testing.expectError(error.InvalidName, result);
}

test "cloneVoice rejects name that exceeds NAME_MAX_LENGTH" {
    var client = Client.init(testing.allocator, .{
        .api_key = "test-key",
        .base_url = "http://127.0.0.1:1",
    });
    defer client.deinit();

    // 31 characters — one over the 30-character limit.
    const long_name = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    try testing.expect(long_name.len == 31);

    const result = client.cloneVoice(testing.allocator, "audio", "sample.wav", long_name, "ssfm-v30");
    try testing.expectError(error.InvalidName, result);
}

// ── deleteVoice — happy path ──────────────────────────────────────────

test "deleteVoice succeeds on 204 No Content" {
    var mock = try InspectingMockServer.init();
    mock.response_status = 204;
    mock.response_body = "";
    try mock.start();
    defer mock.deinit();

    var url_buf: [64]u8 = undefined;
    const base_url = try baseUrlSlice(&url_buf, mock.getPort());

    var client = Client.init(testing.allocator, .{
        .api_key = "test-key",
        .base_url = base_url,
    });
    defer client.deinit();

    try client.deleteVoice("vc_abc123");

    const cap = mock.captured orelse return error.TestNoCapturedRequest;
    try testing.expectEqualStrings("DELETE", cap.method);
    try testing.expect(std.mem.indexOf(u8, cap.path, "vc_abc123") != null);
}

// ── deleteVoice — error path ──────────────────────────────────────────

test "deleteVoice returns error.NotFound on 404" {
    var mock = try InspectingMockServer.init();
    mock.response_status = 404;
    mock.response_body = "{\"detail\":\"voice not found\"}";
    mock.response_content_type = "application/json";
    try mock.start();
    defer mock.deinit();

    var url_buf: [64]u8 = undefined;
    const base_url = try baseUrlSlice(&url_buf, mock.getPort());

    var client = Client.init(testing.allocator, .{
        .api_key = "test-key",
        .base_url = base_url,
    });
    defer client.deinit();

    const result = client.deleteVoice("vc_nonexistent");
    try testing.expectError(error.NotFound, result);
}

// ── Model constant sanity checks ──────────────────────────────────────

test "CLONING_MAX_FILE_SIZE is 25 MB" {
    try testing.expectEqual(@as(usize, 25 * 1024 * 1024), models.CLONING_MAX_FILE_SIZE);
}

test "NAME_MIN_LENGTH is 1 and NAME_MAX_LENGTH is 30" {
    try testing.expectEqual(@as(usize, 1), models.NAME_MIN_LENGTH);
    try testing.expectEqual(@as(usize, 30), models.NAME_MAX_LENGTH);
}

test "CustomVoice struct has expected fields" {
    const v = models.CustomVoice{
        .voice_id = "id",
        .name = "n",
        .model = "m",
    };
    try testing.expectEqualStrings("id", v.voice_id);
    try testing.expectEqualStrings("n", v.name);
    try testing.expectEqualStrings("m", v.model);
}
