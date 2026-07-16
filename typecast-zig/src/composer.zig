const std = @import("std");
const Client = @import("client.zig").Client;
const models = @import("models.zig");

const Allocator = std.mem.Allocator;

pub const ComposerSettings = struct {
    /// Browse available API voices at https://typecast.ai/developers/api/voices.
    voice_id: ?[]const u8 = null,
    model: ?models.TtsModel = null,
    language: ?[]const u8 = null,
    prompt: ?models.TtsPrompt = null,
    output: ?ComposerOutput = null,
    seed: ?i64 = null,
};

pub const ComposerOutput = struct {
    volume: ?i32 = null,
    target_lufs: ?f64 = null,
    audio_pitch: ?i32 = null,
    audio_tempo: ?f64 = null,
    audio_format: ?models.AudioFormat = null,
};

pub const SpeechPart = struct {
    text: []const u8 = "",
    pause_seconds: f64 = 0,
    is_pause: bool = false,
};

const ComposerPart = union(enum) {
    speech: struct {
        text: []u8,
        settings: ComposerSettings,
    },
    pause: f64,
};

pub const SpeechComposer = struct {
    client: *Client,
    settings: ComposerSettings = .{},
    parts: std.ArrayList(ComposerPart) = .empty,

    pub fn init(client: *Client) SpeechComposer {
        return .{ .client = client };
    }

    pub fn deinit(self: *SpeechComposer) void {
        for (self.parts.items) |part| {
            switch (part) {
                .speech => |speech| self.client.allocator.free(speech.text),
                .pause => {},
            }
        }
        self.parts.deinit(self.client.allocator);
    }

    /// Set default voice/model/output options used by following speech parts.
    pub fn defaults(self: *SpeechComposer, settings: ComposerSettings) !void {
        self.settings = mergeSettings(self.settings, settings);
    }

    /// Add one speech segment. Per-segment settings override defaults.
    pub fn say(self: *SpeechComposer, text: []const u8, settings: ComposerSettings) !void {
        try self.parts.append(self.client.allocator, .{
            .speech = .{
                .text = try self.client.allocator.dupe(u8, text),
                .settings = settings,
            },
        });
    }

    /// Add a silent pause in seconds.
    pub fn pause(self: *SpeechComposer, seconds: f64) !void {
        if (!std.math.isFinite(seconds) or seconds <= 0) return error.InvalidPause;
        try self.parts.append(self.client.allocator, .{ .pause = seconds });
    }

    /// Build the per-segment TTS requests. Internal requests always use WAV.
    pub fn segmentRequests(self: *SpeechComposer) !std.ArrayList(models.TtsRequest) {
        var requests: std.ArrayList(models.TtsRequest) = .empty;
        errdefer requests.deinit(self.client.allocator);

        for (self.parts.items) |part| {
            switch (part) {
                .speech => |speech| {
                    const merged = mergeSettings(self.settings, speech.settings);
                    const voice_id = merged.voice_id orelse return error.MissingVoiceId;
                    const model = merged.model orelse .ssfm_v30;
                    var output = toModelOutput(merged.output);
                    output.audio_format = .wav;
                    try requests.append(self.client.allocator, .{
                        .voice_id = voice_id,
                        .text = speech.text,
                        .model = model,
                        .language = merged.language,
                        .prompt = merged.prompt,
                        .output = output,
                        .seed = merged.seed,
                    });
                },
                .pause => {},
            }
        }

        return requests;
    }

    /// Generate composed audio with one Typecast Compose API request.
    pub fn generate(self: *SpeechComposer, requested_format: ?models.AudioFormat) !models.TtsResponse {
        const output_format = try self.resolveOutputFormat(requested_format);
        var body: std.ArrayList(u8) = .empty;
        defer body.deinit(self.client.allocator);
        try body.appendSlice(self.client.allocator, "{\"segments\":[");
        var first = true;
        var has_speech = false;
        for (self.parts.items) |part| {
            switch (part) {
                .pause => |seconds| {
                    if (!std.math.isFinite(seconds) or seconds <= 0) return error.InvalidPause;
                    if (!first) try body.append(self.client.allocator, ',');
                    first = false;
                    try body.writer(self.client.allocator).print("{{\"type\":\"pause\",\"duration_seconds\":{d}}}", .{seconds});
                },
                .speech => |speech| {
                    const merged = mergeSettings(self.settings, speech.settings);
                    const parsed_parts = try parsePauseMarkup(self.client.allocator, speech.text);
                    defer freeSpeechParts(self.client.allocator, parsed_parts);
                    for (parsed_parts) |parsed| {
                        if (parsed.is_pause) {
                            if (!std.math.isFinite(parsed.pause_seconds) or parsed.pause_seconds <= 0) return error.InvalidPause;
                            if (!first) try body.append(self.client.allocator, ',');
                            first = false;
                            try body.writer(self.client.allocator).print("{{\"type\":\"pause\",\"duration_seconds\":{d}}}", .{parsed.pause_seconds});
                            continue;
                        }
                        if (std.mem.trim(u8, parsed.text, " \t\r\n").len == 0) continue;
                        const voice_id = merged.voice_id orelse return error.MissingVoiceId;
                        const model = merged.model orelse .ssfm_v30;
                        var output = toModelOutput(merged.output);
                        output.audio_format = output_format;
                        const request_body = try @import("json.zig").serializeTtsRequest(self.client.allocator, .{
                            .voice_id = voice_id,
                            .text = parsed.text,
                            .model = model,
                            .language = merged.language,
                            .prompt = merged.prompt,
                            .output = output,
                            .seed = merged.seed,
                        });
                        defer self.client.allocator.free(request_body);
                        if (!first) try body.append(self.client.allocator, ',');
                        first = false;
                        try body.appendSlice(self.client.allocator, "{\"type\":\"tts\",");
                        try body.appendSlice(self.client.allocator, request_body[1..]);
                        has_speech = true;
                    }
                },
            }
        }
        if (!has_speech) return error.MissingSpeechSegment;
        try body.appendSlice(self.client.allocator, "]}");
        return self.client.composeTextToSpeechBody(body.items);
    }

    fn resolveOutputFormat(self: *SpeechComposer, requested_format: ?models.AudioFormat) !models.AudioFormat {
        var resolved = requested_format;
        for (self.parts.items) |part| {
            switch (part) {
                .pause => {},
                .speech => |speech| {
                    const parsed_parts = try parsePauseMarkup(self.client.allocator, speech.text);
                    defer freeSpeechParts(self.client.allocator, parsed_parts);
                    const has_text = for (parsed_parts) |parsed| {
                        if (!parsed.is_pause and std.mem.trim(u8, parsed.text, " \t\r\n").len > 0) break true;
                    } else false;
                    if (!has_text) continue;
                    const merged = mergeSettings(self.settings, speech.settings);
                    if (merged.output) |output| if (output.audio_format) |format| {
                        if (resolved) |current| {
                            if (current != format) return error.ConflictingAudioFormats;
                        } else resolved = format;
                    };
                },
            }
        }
        return resolved orelse .wav;
    }
};

pub fn parsePauseMarkup(allocator: Allocator, text: []const u8) ![]SpeechPart {
    var parts: std.ArrayList(SpeechPart) = .empty;
    errdefer freeSpeechParts(allocator, parts.items);

    var text_start: usize = 0;
    var search_start: usize = 0;
    while (std.mem.indexOfPos(u8, text, search_start, "<|")) |start| {
        const value_start = start + 2;
        const end = std.mem.indexOfPos(u8, text, value_start, "|>") orelse break;
        const token = text[value_start..end];
        if (parsePauseToken(token)) |seconds| {
            try parts.append(allocator, .{ .text = try allocator.dupe(u8, text[text_start..start]) });
            try parts.append(allocator, .{ .pause_seconds = seconds, .is_pause = true });
            text_start = end + 2;
            search_start = text_start;
        } else {
            search_start = value_start;
        }
    }

    try parts.append(allocator, .{ .text = try allocator.dupe(u8, text[text_start..]) });
    return parts.toOwnedSlice(allocator);
}

pub fn freeSpeechParts(allocator: Allocator, parts: []SpeechPart) void {
    for (parts) |part| {
        if (!part.is_pause) allocator.free(part.text);
    }
    allocator.free(parts);
}

fn parsePauseToken(token: []const u8) ?f64 {
    if (token.len < 2 or token[token.len - 1] != 's') return null;
    const number = token[0 .. token.len - 1];
    if (number.len == 0) return null;
    for (number) |c| {
        if (!(std.ascii.isDigit(c) or c == '.')) return null;
    }
    const seconds = std.fmt.parseFloat(f64, number) catch return null;
    return if (std.math.isFinite(seconds) and seconds > 0) seconds else null;
}

fn mergeSettings(base: ComposerSettings, overrides: ComposerSettings) ComposerSettings {
    return .{
        .voice_id = overrides.voice_id orelse base.voice_id,
        .model = overrides.model orelse base.model,
        .language = overrides.language orelse base.language,
        .prompt = overrides.prompt orelse base.prompt,
        .output = mergeOutput(base.output, overrides.output),
        .seed = overrides.seed orelse base.seed,
    };
}

fn mergeOutput(base: ?ComposerOutput, overrides: ?ComposerOutput) ?ComposerOutput {
    if (base == null and overrides == null) return null;
    const b = base orelse ComposerOutput{};
    const o = overrides orelse ComposerOutput{};
    return .{
        .volume = o.volume orelse b.volume,
        .target_lufs = o.target_lufs orelse b.target_lufs,
        .audio_pitch = o.audio_pitch orelse b.audio_pitch,
        .audio_tempo = o.audio_tempo orelse b.audio_tempo,
        .audio_format = o.audio_format orelse b.audio_format,
    };
}

fn toModelOutput(output: ?ComposerOutput) models.Output {
    const o = output orelse ComposerOutput{};
    return .{
        .volume = o.volume,
        .target_lufs = o.target_lufs,
        .audio_pitch = o.audio_pitch,
        .audio_tempo = o.audio_tempo,
        .audio_format = o.audio_format,
    };
}
