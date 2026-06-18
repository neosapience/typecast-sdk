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
        if (seconds < 0) return error.InvalidPause;
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

    /// Generate composed audio. WAV output is supported; MP3 conversion should
    /// be performed by the application after receiving the composed WAV.
    pub fn generate(self: *SpeechComposer, requested_format: ?models.AudioFormat) !models.TtsResponse {
        if (requested_format == .mp3) return error.Mp3ConversionNotAvailable;

        var audio_segments: std.ArrayList([]const u8) = .empty;
        defer {
            for (audio_segments.items) |audio| self.client.allocator.free(audio);
            audio_segments.deinit(self.client.allocator);
        }

        var pauses: std.ArrayList(f64) = .empty;
        defer pauses.deinit(self.client.allocator);

        var pending_pause: f64 = 0;
        var has_audio = false;
        for (self.parts.items) |part| {
            switch (part) {
                .pause => |seconds| pending_pause += seconds,
                .speech => |speech| {
                    if (has_audio) {
                        try pauses.append(self.client.allocator, pending_pause);
                    }
                    pending_pause = 0;
                    const merged = mergeSettings(self.settings, speech.settings);
                    const voice_id = merged.voice_id orelse return error.MissingVoiceId;
                    const model = merged.model orelse .ssfm_v30;
                    var output = toModelOutput(merged.output);
                    output.audio_format = .wav;
                    const response = try self.client.textToSpeech(.{
                        .voice_id = voice_id,
                        .text = speech.text,
                        .model = model,
                        .language = merged.language,
                        .prompt = merged.prompt,
                        .output = output,
                        .seed = merged.seed,
                    });
                    try audio_segments.append(self.client.allocator, response.audio_data);
                    has_audio = true;
                },
            }
        }

        const audio_data = try composeWav(self.client.allocator, audio_segments.items, pauses.items);
        return .{
            .audio_data = audio_data,
            .duration = wavDuration(audio_data) catch 0,
            .format = .wav,
        };
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
    return std.fmt.parseFloat(f64, number) catch null;
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

const WavInfo = struct {
    sample_rate: u32,
    pcm_start: usize,
    pcm_len: usize,
};

const PcmSegment = struct {
    bytes: []const u8,
    start_sample: usize,
    end_sample: usize,
};

pub fn composeWav(allocator: Allocator, wavs: []const []const u8, pauses: []const f64) ![]u8 {
    if (wavs.len == 0) return error.EmptyComposition;
    if (pauses.len + 1 != wavs.len) return error.InvalidPauseCount;

    const first = try parseWav(wavs[0]);
    const sample_rate = first.sample_rate;
    var total_samples: usize = 0;
    var pcm_segments = try allocator.alloc(PcmSegment, wavs.len);
    defer allocator.free(pcm_segments);

    for (wavs, 0..) |wav, i| {
        const info = try parseWav(wav);
        if (info.sample_rate != sample_rate) return error.UnsupportedWav;
        const pcm_bytes = wav[info.pcm_start .. info.pcm_start + info.pcm_len];
        pcm_segments[i] = trimZeroSamples(pcm_bytes);
        total_samples += pcm_segments[i].end_sample - pcm_segments[i].start_sample;
        if (i < pauses.len) {
            total_samples += @intFromFloat(@round(pauses[i] * @as(f64, @floatFromInt(sample_rate))));
        }
    }

    const data_len = total_samples * @sizeOf(i16);
    const out = try allocator.alloc(u8, 44 + data_len);
    writeWavHeader(out[0..44], sample_rate, @intCast(data_len));
    var cursor: usize = 44;

    for (pcm_segments, 0..) |segment, i| {
        var sample_index = segment.start_sample;
        while (sample_index < segment.end_sample) : (sample_index += 1) {
            const byte_index = sample_index * 2;
            const sample = std.mem.readInt(i16, segment.bytes[byte_index..][0..2], .little);
            std.mem.writeInt(i16, out[cursor..][0..2], sample, .little);
            cursor += 2;
        }
        if (i < pauses.len) {
            const pause_samples: usize = @intFromFloat(@round(pauses[i] * @as(f64, @floatFromInt(sample_rate))));
            @memset(out[cursor .. cursor + pause_samples * 2], 0);
            cursor += pause_samples * 2;
        }
    }

    return out;
}

fn parseWav(wav: []const u8) !WavInfo {
    if (wav.len < 44) return error.UnsupportedWav;
    if (!std.mem.eql(u8, wav[0..4], "RIFF") or !std.mem.eql(u8, wav[8..12], "WAVE")) return error.UnsupportedWav;
    if (!std.mem.eql(u8, wav[12..16], "fmt ")) return error.UnsupportedWav;
    const audio_format = std.mem.readInt(u16, wav[20..][0..2], .little);
    const channels = std.mem.readInt(u16, wav[22..][0..2], .little);
    const sample_rate = std.mem.readInt(u32, wav[24..][0..4], .little);
    const bits_per_sample = std.mem.readInt(u16, wav[34..][0..2], .little);
    if (audio_format != 1 or channels != 1 or bits_per_sample != 16) return error.UnsupportedWav;
    var cursor: usize = 36;
    while (cursor + 8 <= wav.len) {
        const chunk_id = wav[cursor .. cursor + 4];
        const chunk_size = std.mem.readInt(u32, wav[cursor + 4 ..][0..4], .little);
        const data_start = cursor + 8;
        const data_end = data_start + chunk_size;
        if (data_end > wav.len) return error.UnsupportedWav;
        if (std.mem.eql(u8, chunk_id, "data")) {
            return .{ .sample_rate = sample_rate, .pcm_start = data_start, .pcm_len = chunk_size };
        }
        cursor = data_end;
    }
    return error.UnsupportedWav;
}

fn trimZeroSamples(bytes: []const u8) PcmSegment {
    const sample_count = bytes.len / 2;
    var start: usize = 0;
    var end: usize = sample_count;
    while (start < end and std.mem.readInt(i16, bytes[start * 2 ..][0..2], .little) == 0) start += 1;
    while (end > start and std.mem.readInt(i16, bytes[(end - 1) * 2 ..][0..2], .little) == 0) end -= 1;
    return .{ .bytes = bytes, .start_sample = start, .end_sample = end };
}

fn wavDuration(wav: []const u8) !f64 {
    const info = try parseWav(wav);
    const sample_count = info.pcm_len / @sizeOf(i16);
    return @as(f64, @floatFromInt(sample_count)) / @as(f64, @floatFromInt(info.sample_rate));
}

fn writeWavHeader(header: []u8, sample_rate: u32, data_len: u32) void {
    @memcpy(header[0..4], "RIFF");
    std.mem.writeInt(u32, header[4..][0..4], 36 + data_len, .little);
    @memcpy(header[8..12], "WAVE");
    @memcpy(header[12..16], "fmt ");
    std.mem.writeInt(u32, header[16..][0..4], 16, .little);
    std.mem.writeInt(u16, header[20..][0..2], 1, .little);
    std.mem.writeInt(u16, header[22..][0..2], 1, .little);
    std.mem.writeInt(u32, header[24..][0..4], sample_rate, .little);
    std.mem.writeInt(u32, header[28..][0..4], sample_rate * 2, .little);
    std.mem.writeInt(u16, header[32..][0..2], 2, .little);
    std.mem.writeInt(u16, header[34..][0..2], 16, .little);
    @memcpy(header[36..40], "data");
    std.mem.writeInt(u32, header[40..][0..4], data_len, .little);
}

pub fn testWav(allocator: Allocator, samples: []const i16, sample_rate: u32) ![]u8 {
    const out = try allocator.alloc(u8, 44 + samples.len * 2);
    writeWavHeader(out[0..44], sample_rate, @intCast(samples.len * 2));
    var cursor: usize = 44;
    for (samples) |sample| {
        std.mem.writeInt(i16, out[cursor..][0..2], sample, .little);
        cursor += 2;
    }
    return out;
}

pub fn testReadPcm(allocator: Allocator, wav: []const u8) ![]i16 {
    const info = try parseWav(wav);
    const bytes = wav[info.pcm_start .. info.pcm_start + info.pcm_len];
    const samples = try allocator.alloc(i16, bytes.len / 2);
    for (samples, 0..) |*sample, i| {
        sample.* = std.mem.readInt(i16, bytes[i * 2 ..][0..2], .little);
    }
    return samples;
}
