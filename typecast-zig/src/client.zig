const std = @import("std");
const builtin = @import("builtin");
const models = @import("models.zig");
const json_helpers = @import("json.zig");
const timestamps = @import("timestamps.zig");
const composer = @import("composer.zig");

const SDK_VERSION = "0.2.6";
const USER_AGENT_DEFAULT = "typecast-zig/" ++ SDK_VERSION ++ " Zig/unknown std-http (base=default; os=" ++ osName() ++ "; arch=" ++ archName() ++ "; sdk_env=zig; platform=server)";
const USER_AGENT_CUSTOM = "typecast-zig/" ++ SDK_VERSION ++ " Zig/unknown std-http (base=custom; os=" ++ osName() ++ "; arch=" ++ archName() ++ "; sdk_env=zig; platform=server)";

pub const Client = struct {
    allocator: std.mem.Allocator,
    http_client: std.http.Client,
    api_key: []const u8,
    base_url: []const u8,

    pub const Config = struct {
        api_key: []const u8 = "",
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
            .http_client = .{
                .allocator = allocator,
            },
            .api_key = config.api_key,
            .base_url = config.base_url,
        };
    }

    pub fn deinit(self: *Client) void {
        self.http_client.deinit();
    }

    fn userAgent(self: *const Client) []const u8 {
        return if (isDefaultBaseUrl(self.base_url)) USER_AGENT_DEFAULT else USER_AGENT_CUSTOM;
    }

    // ── Public API methods ────────────────────────────────────────────

    /// Create a composed speech builder for multi-speaker audio and pauses.
    ///
    /// All speech and pause segments are sent together to generate composed
    /// audio with one backend request.
    pub fn composeSpeech(self: *Client) composer.SpeechComposer {
        return composer.SpeechComposer.init(self);
    }

    /// POST /v1/text-to-speech — full audio response
    pub fn textToSpeech(self: *Client, request: models.TtsRequest) !models.TtsResponse {
        const body = try json_helpers.serializeTtsRequest(self.allocator, request);
        defer self.allocator.free(body);

        const path = "/v1/text-to-speech";
        try self.validateApiKey();
        const uri = try buildUri(self.base_url, path, null);

        const headers: []const std.http.Header = if (!hasApiKey(self.api_key))
            &.{
                .{ .name = "User-Agent", .value = self.userAgent() },
                .{ .name = "Content-Type", .value = "application/json" },
            }
        else
            &.{
                .{ .name = "User-Agent", .value = self.userAgent() },
                .{ .name = "X-API-KEY", .value = self.api_key },
                .{ .name = "Content-Type", .value = "application/json" },
            };
        var req = try self.http_client.request(.POST, uri, .{ .extra_headers = headers });
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

    pub fn composeTextToSpeechBody(self: *Client, body: []const u8) !models.TtsResponse {
        try self.validateApiKey();
        const uri = try buildUri(self.base_url, "/v1/text-to-speech/compose", null);
        const headers: []const std.http.Header = if (!hasApiKey(self.api_key))
            &.{ .{ .name = "User-Agent", .value = self.userAgent() }, .{ .name = "Content-Type", .value = "application/json" } }
        else
            &.{ .{ .name = "User-Agent", .value = self.userAgent() }, .{ .name = "X-API-KEY", .value = self.api_key }, .{ .name = "Content-Type", .value = "application/json" } };
        var req = try self.http_client.request(.POST, uri, .{ .extra_headers = headers });
        defer req.deinit();
        try req.sendBodyComplete(@constCast(body));
        var redirect_buf: [4096]u8 = undefined;
        var response = try req.receiveHead(&redirect_buf);
        try mapStatusError(response.head.status);
        const duration = parseDurationHeader(response.head.bytes);
        const format = detectFormat(response.head.content_type);
        var transfer_buf: [16384]u8 = undefined;
        const audio_data = try response.reader(&transfer_buf).allocRemaining(self.allocator, .unlimited);
        return .{ .audio_data = audio_data, .duration = duration, .format = format };
    }

    /// Generate audio and write it to a file.
    ///
    /// Browse available API voices at https://typecast.ai/developers/api/voices.
    ///
    /// The returned response owns `audio_data`; free it with the client's allocator.
    pub fn generateToFile(
        self: *Client,
        path: []const u8,
        request: models.GenerateToFileRequest,
    ) !models.TtsResponse {
        if (std.mem.trim(u8, path, " \t\r\n").len == 0) {
            return error.InvalidPath;
        }

        var output = request.output;
        if (inferAudioFormatFromPath(path)) |format| {
            if (output) |*out| {
                if (out.audio_format == null) out.audio_format = format;
            } else {
                output = .{ .audio_format = format };
            }
        }

        const response = try self.textToSpeech(.{
            .voice_id = request.voice_id,
            .text = request.text,
            .model = request.model,
            .language = request.language,
            .prompt = request.prompt,
            .output = output,
            .seed = request.seed,
        });
        errdefer self.allocator.free(response.audio_data);

        const file = try std.fs.cwd().createFile(path, .{});
        defer file.close();
        try file.writeAll(response.audio_data);
        return response;
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
        try self.validateApiKey();
        const uri = try buildUri(self.base_url, path, null);

        const headers: []const std.http.Header = if (!hasApiKey(self.api_key))
            &.{
                .{ .name = "User-Agent", .value = self.userAgent() },
                .{ .name = "Content-Type", .value = "application/json" },
            }
        else
            &.{
                .{ .name = "User-Agent", .value = self.userAgent() },
                .{ .name = "X-API-KEY", .value = self.api_key },
                .{ .name = "Content-Type", .value = "application/json" },
            };
        var req = try self.http_client.request(.POST, uri, .{ .extra_headers = headers });
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
        try self.validateApiKey();
        const uri = try buildUri(self.base_url, path, query);

        const headers: []const std.http.Header = if (!hasApiKey(self.api_key))
            &.{
                .{ .name = "User-Agent", .value = self.userAgent() },
                .{ .name = "Content-Type", .value = "application/json" },
            }
        else
            &.{
                .{ .name = "User-Agent", .value = self.userAgent() },
                .{ .name = "X-API-KEY", .value = self.api_key },
                .{ .name = "Content-Type", .value = "application/json" },
            };
        var req = try self.http_client.request(.POST, uri, .{ .extra_headers = headers });
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

    /// GET /v1/voices/recommendations
    ///
    /// Recommendation results include only voice_id, voice_name, and score.
    /// Use getVoiceV2 or getVoicesV2 when detailed voice metadata is needed.
    pub fn recommendVoices(self: *Client, query: []const u8, count: u8) ![]models.RecommendedVoice {
        const resolved_count = if (count == 0) 5 else count;
        if (resolved_count > 10) return error.InvalidCount;

        var query_buf: [2048]u8 = undefined;
        var stream = std.io.fixedBufferStream(&query_buf);
        const writer = stream.writer();
        writer.writeAll("query=") catch return error.QueryTooLong;
        percentEncode(writer, query) catch return error.QueryTooLong;
        writer.print("&count={d}", .{resolved_count}) catch return error.QueryTooLong;

        const body = try self.doGet("/v1/voices/recommendations", query_buf[0..stream.pos]);
        defer self.allocator.free(body);
        return json_helpers.parseRecommendedVoices(self.allocator, body);
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
        try self.validateApiKey();
        const uri = try buildUri(self.base_url, path, null);

        const headers: []const std.http.Header = if (!hasApiKey(self.api_key))
            &.{
                .{ .name = "User-Agent", .value = self.userAgent() },
                .{ .name = "Content-Type", .value = ct },
            }
        else
            &.{
                .{ .name = "User-Agent", .value = self.userAgent() },
                .{ .name = "X-API-KEY", .value = self.api_key },
                .{ .name = "Content-Type", .value = ct },
            };
        var req = try self.http_client.request(.POST, uri, .{ .extra_headers = headers });
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

        try self.validateApiKey();
        const uri = try buildUri(self.base_url, path, null);

        const headers: []const std.http.Header = if (!hasApiKey(self.api_key))
            &.{.{ .name = "User-Agent", .value = self.userAgent() }}
        else
            &.{
                .{ .name = "User-Agent", .value = self.userAgent() },
                .{ .name = "X-API-KEY", .value = self.api_key },
            };
        var req = try self.http_client.request(.DELETE, uri, .{ .extra_headers = headers });
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
        try self.validateApiKey();
        const uri = try buildUri(self.base_url, path, query);

        const headers: []const std.http.Header = if (!hasApiKey(self.api_key))
            &.{.{ .name = "User-Agent", .value = self.userAgent() }}
        else
            &.{
                .{ .name = "User-Agent", .value = self.userAgent() },
                .{ .name = "X-API-KEY", .value = self.api_key },
            };
        var req = try self.http_client.request(.GET, uri, .{ .extra_headers = headers });
        defer req.deinit();

        try req.sendBodiless();

        var redirect_buf: [4096]u8 = undefined;
        var response = try req.receiveHead(&redirect_buf);

        try mapStatusError(response.head.status);

        var transfer_buf: [16384]u8 = undefined;
        const reader = response.reader(&transfer_buf);
        return reader.allocRemaining(self.allocator, .unlimited);
    }

    fn validateApiKey(self: *const Client) !void {
        if (!hasApiKey(self.api_key) and isDefaultBaseUrl(self.base_url)) {
            return error.MissingApiKey;
        }
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

fn hasApiKey(api_key: []const u8) bool {
    return std.mem.trim(u8, api_key, " \t\r\n").len > 0;
}

fn inferAudioFormatFromPath(path: []const u8) ?models.AudioFormat {
    if (Client.endsWithIgnoreCase(path, ".mp3")) return .mp3;
    if (Client.endsWithIgnoreCase(path, ".wav")) return .wav;
    return null;
}

fn isDefaultBaseUrl(base_url: []const u8) bool {
    const trimmed_space = std.mem.trim(u8, base_url, " \t\r\n");
    const normalized = std.mem.trimRight(u8, trimmed_space, "/");
    return std.ascii.eqlIgnoreCase(normalized, "https://api.typecast.ai");
}

fn osName() []const u8 {
    return switch (builtin.target.os.tag) {
        .macos => "macos",
        .windows => "windows",
        .linux => "linux",
        .ios => "ios",
        else => "unknown",
    };
}

fn archName() []const u8 {
    return switch (builtin.target.cpu.arch) {
        .x86_64 => "x64",
        .aarch64 => "arm64",
        .x86 => "x86",
        .arm => "arm",
        else => "unknown",
    };
}

test "user agent includes sdk metadata" {
    var client = Client.init(std.testing.allocator, .{
        .api_key = "test-key",
        .base_url = "https://api.typecast.ai",
    });
    defer client.deinit();

    const ua = client.userAgent();
    try std.testing.expect(std.mem.indexOf(u8, ua, "typecast-zig/") != null);
    try std.testing.expect(std.mem.indexOf(u8, ua, "sdk_env=zig") != null);
    try std.testing.expect(std.mem.indexOf(u8, ua, "platform=server") != null);
}
