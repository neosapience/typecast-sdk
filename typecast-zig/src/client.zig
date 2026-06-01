const std = @import("std");
const models = @import("models.zig");
const json_helpers = @import("json.zig");
const timestamps = @import("timestamps.zig");

pub const Client = struct {
    allocator: std.mem.Allocator,
    http_client: std.http.Client,
    api_key: []const u8,
    base_url: []const u8,

    pub const Config = struct {
        api_key: []const u8,
        base_url: []const u8 = "https://api.typecast.ai",
    };

    pub const ApiError = error{
        BadRequest,
        Unauthorized,
        PaymentRequired,
        NotFound,
        UnprocessableEntity,
        RateLimited,
        InternalServerError,
        UnexpectedStatus,
    };

    pub fn init(allocator: std.mem.Allocator, config: Config) Client {
        return .{
            .allocator = allocator,
            .http_client = .{ .allocator = allocator },
            .api_key = config.api_key,
            .base_url = config.base_url,
        };
    }

    pub fn deinit(self: *Client) void {
        self.http_client.deinit();
    }

    // ── Public API methods ────────────────────────────────────────────

    /// POST /v1/text-to-speech — full audio response
    pub fn textToSpeech(self: *Client, request: models.TtsRequest) !models.TtsResponse {
        const body = try json_helpers.serializeTtsRequest(self.allocator, request);
        defer self.allocator.free(body);

        const path = "/v1/text-to-speech";
        const uri = try buildUri(self.base_url, path, null);

        var req = try self.http_client.request(.POST, uri, .{
            .extra_headers = &.{
                .{ .name = "X-API-KEY", .value = self.api_key },
                .{ .name = "Content-Type", .value = "application/json" },
            },
        });
        defer req.deinit();

        try req.sendBodyComplete(@constCast(body));

        var redirect_buf: [4096]u8 = undefined;
        var response = try req.receiveHead(&redirect_buf);

        try mapStatusError(response.head.status);

        // Extract headers before calling reader() which invalidates them.
        const duration = parseDurationHeader(response.head.bytes);
        const format = detectFormat(response.head.content_type);

        // Read body
        var transfer_buf: [16384]u8 = undefined;
        const reader = response.reader(&transfer_buf);
        const audio_data = try reader.allocRemaining(self.allocator, .unlimited);

        return .{
            .audio_data = audio_data,
            .duration = duration,
            .format = format,
        };
    }

    /// POST /v1/text-to-speech/stream — chunked audio streaming
    pub fn textToSpeechStream(
        self: *Client,
        request: models.TtsRequestStream,
        on_chunk: *const fn ([]const u8) anyerror!void,
    ) !void {
        const body = try json_helpers.serializeTtsRequestStream(self.allocator, request);
        defer self.allocator.free(body);

        const path = "/v1/text-to-speech/stream";
        const uri = try buildUri(self.base_url, path, null);

        var req = try self.http_client.request(.POST, uri, .{
            .extra_headers = &.{
                .{ .name = "X-API-KEY", .value = self.api_key },
                .{ .name = "Content-Type", .value = "application/json" },
            },
        });
        defer req.deinit();

        try req.sendBodyComplete(@constCast(body));

        var redirect_buf: [4096]u8 = undefined;
        var response = try req.receiveHead(&redirect_buf);

        try mapStatusError(response.head.status);

        var transfer_buf: [16384]u8 = undefined;
        var reader = response.reader(&transfer_buf);

        // Read body and forward to callback.  In Zig 0.15 the Reader
        // vtable's readVec does not reliably return already-buffered HTTP
        // data, so we use allocRemaining which is implemented via the
        // working stream() path.
        const response_body = try reader.allocRemaining(self.allocator, .unlimited);
        defer self.allocator.free(response_body);
        if (response_body.len > 0) {
            try on_chunk(response_body);
        }
    }

    /// POST /v1/text-to-speech/with-timestamps — returns audio + alignment data.
    ///
    /// `granularity` controls which alignment segments the API returns:
    ///   - null or ""  -> API default (both words and characters)
    ///   - "word"      -> word-level only
    ///   - "char"      -> character-level only
    ///
    /// The returned `TTSWithTimestampsResponse` owns all allocated memory.
    /// Call `response.deinit()` to release it.
    pub fn textToSpeechWithTimestamps(
        self: *Client,
        request: timestamps.TTSRequestWithTimestamps,
        granularity: ?[]const u8,
    ) !timestamps.TTSWithTimestampsResponse {
        // Validate granularity against the allowed whitelist before sending.
        if (granularity) |g| {
            if (g.len > 0 and !std.mem.eql(u8, g, "word") and !std.mem.eql(u8, g, "char")) {
                return error.InvalidGranularity;
            }
        }
        const body = try timestamps.serializeTtsRequestWithTimestamps(
            self.allocator,
            request,
            null, // granularity is sent as a query parameter, not in the body
        );
        defer self.allocator.free(body);

        const path = "/v1/text-to-speech/with-timestamps";
        // Granularity is a query parameter: ?granularity=word or ?granularity=char
        var query_buf: [64]u8 = undefined;
        const query: ?[]const u8 = if (granularity) |g| blk: {
            if (g.len == 0) break :blk null;
            const q = std.fmt.bufPrint(&query_buf, "granularity={s}", .{g}) catch break :blk null;
            break :blk q;
        } else null;
        const uri = try buildUri(self.base_url, path, query);

        var req = try self.http_client.request(.POST, uri, .{
            .extra_headers = &.{
                .{ .name = "X-API-KEY", .value = self.api_key },
                .{ .name = "Content-Type", .value = "application/json" },
            },
        });
        defer req.deinit();

        try req.sendBodyComplete(@constCast(body));

        var redirect_buf: [4096]u8 = undefined;
        var response = try req.receiveHead(&redirect_buf);

        try mapStatusError(response.head.status);

        var transfer_buf: [65536]u8 = undefined;
        const reader = response.reader(&transfer_buf);
        const json_data = try reader.allocRemaining(self.allocator, .unlimited);
        defer self.allocator.free(json_data);

        return timestamps.parseWithTimestampsResponse(self.allocator, json_data);
    }

    /// GET /v1/users/me/subscription
    pub fn getMySubscription(self: *Client) !models.SubscriptionResponse {
        const path = "/v1/users/me/subscription";
        const body = try self.doGet(path, null);
        defer self.allocator.free(body);
        return json_helpers.parseSubscriptionResponse(self.allocator, body);
    }

    /// GET /v1/voices
    pub fn getVoices(self: *Client, model: ?models.TtsModel) ![]models.Voice {
        var query_buf: [256]u8 = undefined;
        const query = buildVoicesQuery(&query_buf, model);
        const body = try self.doGet("/v1/voices", query);
        defer self.allocator.free(body);
        return json_helpers.parseVoices(self.allocator, body);
    }

    /// GET /v2/voices
    pub fn getVoicesV2(self: *Client, filter: ?models.VoicesV2Filter) ![]models.VoiceV2 {
        var query_buf: [512]u8 = undefined;
        const query = try buildVoicesV2Query(&query_buf, filter);
        const body = try self.doGet("/v2/voices", query);
        defer self.allocator.free(body);
        return json_helpers.parseVoicesV2(self.allocator, body);
    }

    /// GET /v2/voices?voice_id=X — returns first match or error.NotFound
    pub fn getVoiceV2(self: *Client, voice_id: []const u8, model: ?models.TtsModel) !models.VoiceV2 {
        var query_buf: [512]u8 = undefined;
        var stream = std.io.fixedBufferStream(&query_buf);
        const writer = stream.writer();
        writer.print("voice_id={s}", .{voice_id}) catch return error.QueryTooLong;
        if (model) |m| {
            writer.print("&model={s}", .{m.toString()}) catch return error.QueryTooLong;
        }
        const query = query_buf[0..stream.pos];

        const body = try self.doGet("/v2/voices", query);
        defer self.allocator.free(body);

        const voices = try json_helpers.parseVoicesV2(self.allocator, body);
        if (voices.len == 0) {
            self.allocator.free(voices);
            return error.NotFound;
        }
        // Return the first match; free allocations for the rest, then the slice.
        const result = voices[0];
        for (voices[1..]) |v| {
            self.allocator.free(v.voice_id);
            self.allocator.free(v.voice_name);
            for (v.models) |mi| {
                self.allocator.free(mi.version);
                for (mi.emotions) |e| self.allocator.free(e);
                self.allocator.free(mi.emotions);
            }
            self.allocator.free(v.models);
            if (v.use_cases) |cases| {
                for (cases) |c| self.allocator.free(c);
                self.allocator.free(cases);
            }
        }
        self.allocator.free(voices);
        return result;
    }

    /// POST /v1/voices/clone — upload audio and create a custom voice.
    ///
    /// - `audio`    : raw audio bytes (WAV or MP3, max 25 MB)
    /// - `filename` : original filename including extension (used for MIME detection)
    /// - `name`     : display name for the new voice (1–30 characters)
    /// - `model`    : TTS model string, e.g. "ssfm-v21" or "ssfm-v30"
    ///
    /// The returned `CustomVoice` owns all string memory; free with
    /// `allocator.free(result.voice_id)` etc. when done.
    pub fn cloneVoice(
        self: *Client,
        allocator: std.mem.Allocator,
        audio: []const u8,
        filename: []const u8,
        name: []const u8,
        model: []const u8,
    ) !models.CustomVoice {
        if (name.len < models.NAME_MIN_LENGTH or name.len > models.NAME_MAX_LENGTH)
            return error.InvalidName;
        if (audio.len > models.CLONING_MAX_FILE_SIZE)
            return error.AudioTooLarge;

        const boundary = "----TypecastZigBoundary1234567890AB";
        const mime = guessAudioMime(filename);

        // Build multipart body using a growable buffer.
        var aw: std.io.Writer.Allocating = .init(allocator);
        errdefer aw.deinit();

        // Part: name
        try aw.writer.print("--{s}\r\n", .{boundary});
        try aw.writer.print("Content-Disposition: form-data; name=\"name\"\r\n\r\n", .{});
        try aw.writer.print("{s}\r\n", .{name});

        // Part: model
        try aw.writer.print("--{s}\r\n", .{boundary});
        try aw.writer.print("Content-Disposition: form-data; name=\"model\"\r\n\r\n", .{});
        try aw.writer.print("{s}\r\n", .{model});

        // Part: file
        try aw.writer.print("--{s}\r\n", .{boundary});
        try aw.writer.print("Content-Disposition: form-data; name=\"file\"; filename=\"{s}\"\r\n", .{filename});
        try aw.writer.print("Content-Type: {s}\r\n\r\n", .{mime});
        try aw.writer.writeAll(audio);
        try aw.writer.print("\r\n--{s}--\r\n", .{boundary});
        try aw.writer.flush();

        const body_bytes = try aw.toOwnedSlice();
        defer allocator.free(body_bytes);

        // Content-Type header includes boundary
        const ct = try std.fmt.allocPrint(
            allocator,
            "multipart/form-data; boundary={s}",
            .{boundary},
        );
        defer allocator.free(ct);

        const path = "/v1/voices/clone";
        const uri = try buildUri(self.base_url, path, null);

        var req = try self.http_client.request(.POST, uri, .{
            .extra_headers = &.{
                .{ .name = "X-API-KEY", .value = self.api_key },
                .{ .name = "Content-Type", .value = ct },
            },
        });
        defer req.deinit();

        try req.sendBodyComplete(@constCast(body_bytes));

        var redirect_buf: [4096]u8 = undefined;
        var response = try req.receiveHead(&redirect_buf);

        try mapStatusError(response.head.status);

        var transfer_buf: [16384]u8 = undefined;
        const reader = response.reader(&transfer_buf);
        const json_data = try reader.allocRemaining(allocator, .unlimited);
        defer allocator.free(json_data);

        return json_helpers.parseCustomVoice(allocator, json_data);
    }

    /// DELETE /v1/voices/{voice_id} — remove a custom (cloned) voice.
    pub fn deleteVoice(self: *Client, voice_id: []const u8) !void {
        const path = try std.fmt.allocPrint(self.allocator, "/v1/voices/{s}", .{voice_id});
        defer self.allocator.free(path);

        const uri = try buildUri(self.base_url, path, null);

        var req = try self.http_client.request(.DELETE, uri, .{
            .extra_headers = &.{
                .{ .name = "X-API-KEY", .value = self.api_key },
            },
        });
        defer req.deinit();

        try req.sendBodiless();

        var redirect_buf: [4096]u8 = undefined;
        var response = try req.receiveHead(&redirect_buf);

        try mapStatusError(response.head.status);

        // Drain body (204 No Content has no body, 200 may have one — discard either way)
        var transfer_buf: [4096]u8 = undefined;
        const reader = response.reader(&transfer_buf);
        const drain = try reader.allocRemaining(self.allocator, .unlimited);
        self.allocator.free(drain);
    }

    // ── Internal helpers ──────────────────────────────────────────────

    fn doGet(self: *Client, path: []const u8, query: ?[]const u8) ![]u8 {
        const uri = try buildUri(self.base_url, path, query);

        var req = try self.http_client.request(.GET, uri, .{
            .extra_headers = &.{
                .{ .name = "X-API-KEY", .value = self.api_key },
            },
        });
        defer req.deinit();

        try req.sendBodiless();

        var redirect_buf: [4096]u8 = undefined;
        var response = try req.receiveHead(&redirect_buf);

        try mapStatusError(response.head.status);

        var transfer_buf: [16384]u8 = undefined;
        const reader = response.reader(&transfer_buf);
        return reader.allocRemaining(self.allocator, .unlimited);
    }

    fn buildUri(base_url: []const u8, path: []const u8, query: ?[]const u8) !std.Uri {
        var uri = std.Uri.parse(base_url) catch return error.InvalidBaseUrl;
        uri.path = .{ .raw = path };
        uri.query = if (query) |q| .{ .raw = q } else null;
        return uri;
    }

    fn mapStatusError(status: std.http.Status) ApiError!void {
        switch (status) {
            .ok => return,
            .bad_request => return error.BadRequest,
            .unauthorized => return error.Unauthorized,
            .payment_required => return error.PaymentRequired,
            .not_found => return error.NotFound,
            .unprocessable_entity => return error.UnprocessableEntity,
            .too_many_requests => return error.RateLimited,
            .internal_server_error => return error.InternalServerError,
            else => {
                // Accept any 2xx status as success
                if (@intFromEnum(status) >= 200 and @intFromEnum(status) < 300) return;
                return error.UnexpectedStatus;
            },
        }
    }

    fn parseDurationHeader(head_bytes: []const u8) f64 {
        // Search raw header bytes for X-Audio-Duration header (case-insensitive per RFC 7230)
        const needle = "X-Audio-Duration:";
        // Convert header bytes to lowercase for case-insensitive search
        var lower_buf: [8192]u8 = undefined;
        const len = @min(head_bytes.len, lower_buf.len);
        const lower = lower_buf[0..len];
        for (head_bytes[0..len], 0..) |c, idx| {
            lower[idx] = std.ascii.toLower(c);
        }
        const lower_needle = "x-audio-duration:";
        if (std.mem.indexOf(u8, lower, lower_needle)) |pos| {
            const after = head_bytes[pos + needle.len ..];
            // Find end of line
            const end = std.mem.indexOf(u8, after, "\r\n") orelse after.len;
            const value_str = std.mem.trim(u8, after[0..end], " \t");
            return std.fmt.parseFloat(f64, value_str) catch 0.0;
        }
        return 0.0;
    }

    fn detectFormat(content_type: ?[]const u8) models.AudioFormat {
        if (content_type) |ct| {
            if (std.mem.indexOf(u8, ct, "mp3") != null or
                std.mem.indexOf(u8, ct, "mpeg") != null)
            {
                return .mp3;
            }
        }
        return .wav;
    }

    fn buildVoicesQuery(buf: *[256]u8, model: ?models.TtsModel) ?[]const u8 {
        var stream = std.io.fixedBufferStream(buf);
        const writer = stream.writer();
        writer.print("page=0&page_size=100", .{}) catch return null;
        if (model) |m| {
            writer.print("&model={s}", .{m.toString()}) catch return null;
        }
        return buf[0..stream.pos];
    }

    fn buildVoicesV2Query(buf: *[512]u8, filter: ?models.VoicesV2Filter) error{QueryTooLong}!?[]const u8 {
        const f = filter orelse return null;
        var stream = std.io.fixedBufferStream(buf);
        const writer = stream.writer();
        var has_param = false;

        // model, gender, and age use enum .toString() — known safe ASCII,
        // no percent-encoding needed.
        if (f.model) |m| {
            writer.print("model={s}", .{m.toString()}) catch return error.QueryTooLong;
            has_param = true;
        }
        if (f.gender) |g| {
            if (has_param) writer.writeByte('&') catch return error.QueryTooLong;
            writer.print("gender={s}", .{g.toString()}) catch return error.QueryTooLong;
            has_param = true;
        }
        if (f.age) |a| {
            if (has_param) writer.writeByte('&') catch return error.QueryTooLong;
            writer.print("age={s}", .{a.toString()}) catch return error.QueryTooLong;
            has_param = true;
        }
        if (f.use_cases) |uc| {
            if (has_param) writer.writeByte('&') catch return error.QueryTooLong;
            writer.writeAll("use_cases=") catch return error.QueryTooLong;
            percentEncode(writer, uc) catch return error.QueryTooLong;
            has_param = true;
        }

        if (!has_param) return null;
        return buf[0..stream.pos];
    }

    /// Minimal percent-encoding for query parameter values (RFC 3986).
    /// Passes unreserved characters through; encodes everything else as %XX.
    fn percentEncode(writer: anytype, input: []const u8) !void {
        for (input) |c| {
            if (std.ascii.isAlphanumeric(c) or c == '-' or c == '_' or c == '.' or c == '~') {
                try writer.writeByte(c);
            } else {
                try writer.print("%{X:0>2}", .{c});
            }
        }
    }

    /// Guess the MIME type from an audio filename extension.
    fn guessAudioMime(filename: []const u8) []const u8 {
        if (endsWithIgnoreCase(filename, ".wav")) return "audio/wav";
        if (endsWithIgnoreCase(filename, ".mp3")) return "audio/mpeg";
        if (endsWithIgnoreCase(filename, ".ogg")) return "audio/ogg";
        if (endsWithIgnoreCase(filename, ".flac")) return "audio/flac";
        if (endsWithIgnoreCase(filename, ".m4a")) return "audio/mp4";
        return "application/octet-stream";
    }

    fn endsWithIgnoreCase(s: []const u8, suffix: []const u8) bool {
        if (s.len < suffix.len) return false;
        const tail = s[s.len - suffix.len ..];
        for (tail, suffix) |a, b| {
            if (std.ascii.toLower(a) != std.ascii.toLower(b)) return false;
        }
        return true;
    }
};
