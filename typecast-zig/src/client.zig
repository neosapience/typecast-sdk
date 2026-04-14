const std = @import("std");
const models = @import("models.zig");
const json_helpers = @import("json.zig");

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
};
