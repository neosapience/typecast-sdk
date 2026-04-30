const std = @import("std");
const Allocator = std.mem.Allocator;
const models = @import("models.zig");
const json_helpers = @import("json.zig");

// ── Constants ──────────────────────────────────────────────────────────

const CAPTION_MAX_SECONDS: f64 = 7.0;
const CAPTION_MAX_CHARS: usize = 42;

// Sentence-ending terminators.  Full-width punctuation as raw UTF-8.
const TERMINATORS = [_][]const u8{
    ".",
    "?",
    "!",
    "\xe3\x80\x82", // 。 U+3002
    "\xef\xbc\x9f", // ？ U+FF1F
    "\xef\xbc\x81", // ！ U+FF01
};

// ── Public types ───────────────────────────────────────────────────────

/// A single alignment segment (word or character) returned by the API.
pub const AlignmentSegmentWord = struct {
    text: []const u8,
    start: f64,
    end: f64,
};

/// Alias — both word and character segments share the same shape.
pub const AlignmentSegmentCharacter = AlignmentSegmentWord;

/// Request struct for POST /v1/text-to-speech/with-timestamps.
pub const TTSRequestWithTimestamps = struct {
    voice_id: []const u8,
    text: []const u8,
    model: models.TtsModel,
    language: ?[]const u8 = null,
    prompt: ?models.TtsPrompt = null,
    output: ?models.Output = null,
    seed: ?i64 = null,
};

/// Parsed response from POST /v1/text-to-speech/with-timestamps.
///
/// Memory ownership: all slices are owned by this struct.  Call `deinit` to
/// release them.
pub const TTSWithTimestampsResponse = struct {
    /// Base-64-encoded audio string (as received from the API).
    audio_base64: []const u8,
    audio_format: []const u8,
    audio_duration: f64,
    /// Alignment segments at word granularity; null when not requested.
    words: ?[]AlignmentSegmentWord,
    /// Alignment segments at character granularity; null when not requested.
    characters: ?[]AlignmentSegmentCharacter,

    allocator: Allocator,

    /// Free all memory owned by this response.
    pub fn deinit(self: *TTSWithTimestampsResponse) void {
        self.allocator.free(self.audio_base64);
        self.allocator.free(self.audio_format);
        if (self.words) |ws| {
            for (ws) |w| self.allocator.free(w.text);
            self.allocator.free(ws);
        }
        if (self.characters) |cs| {
            for (cs) |c| self.allocator.free(c.text);
            self.allocator.free(cs);
        }
    }

    /// Decode the base-64 audio field and return the raw bytes.
    /// Caller owns the returned slice.
    pub fn audioBytes(self: *const TTSWithTimestampsResponse, allocator: Allocator) ![]u8 {
        const decoder = std.base64.standard.Decoder;
        const max_len = try decoder.calcSizeForSlice(self.audio_base64);
        const buf = try allocator.alloc(u8, max_len);
        errdefer allocator.free(buf);
        try decoder.decode(buf, self.audio_base64);
        return buf;
    }

    /// Write the decoded audio to a file at `path`.
    pub fn saveAudio(self: *const TTSWithTimestampsResponse, path: []const u8, allocator: Allocator) !void {
        const bytes = try self.audioBytes(allocator);
        defer allocator.free(bytes);
        const file = try std.fs.cwd().createFile(path, .{});
        defer file.close();
        try file.writeAll(bytes);
    }

    /// Generate an SRT subtitle string from the alignment data.
    /// Caller owns the returned slice.
    pub fn toSrt(self: *const TTSWithTimestampsResponse, allocator: Allocator) ![]u8 {
        const result = pickSegments(self) orelse return error.NoSegments;
        const segs = result[0];
        const word_mode = result[1];

        const cues = try groupIntoCues(segs, word_mode, allocator);
        defer {
            for (cues) |c| allocator.free(c.text);
            allocator.free(cues);
        }

        var aw: std.io.Writer.Allocating = .init(allocator);
        errdefer aw.deinit();

        for (cues, 0..) |cue, i| {
            try aw.writer.print("{d}\n", .{i + 1});
            try writeSrtTime(&aw.writer, cue.start);
            try aw.writer.writeAll(" --> ");
            try writeSrtTime(&aw.writer, cue.end);
            try aw.writer.writeByte('\n');
            try aw.writer.writeAll(cue.text);
            try aw.writer.writeAll("\n\n");
        }

        try aw.writer.flush();
        return aw.toOwnedSlice();
    }

    /// Generate a WebVTT subtitle string from the alignment data.
    /// Caller owns the returned slice.
    pub fn toVtt(self: *const TTSWithTimestampsResponse, allocator: Allocator) ![]u8 {
        const result = pickSegments(self) orelse return error.NoSegments;
        const segs = result[0];
        const word_mode = result[1];

        const cues = try groupIntoCues(segs, word_mode, allocator);
        defer {
            for (cues) |c| allocator.free(c.text);
            allocator.free(cues);
        }

        var aw: std.io.Writer.Allocating = .init(allocator);
        errdefer aw.deinit();

        try aw.writer.writeAll("WEBVTT\n\n");

        for (cues) |cue| {
            try writeVttTime(&aw.writer, cue.start);
            try aw.writer.writeAll(" --> ");
            try writeVttTime(&aw.writer, cue.end);
            try aw.writer.writeByte('\n');
            try aw.writer.writeAll(cue.text);
            try aw.writer.writeAll("\n\n");
        }

        try aw.writer.flush();
        return aw.toOwnedSlice();
    }
};

// ── Internal: caption cue ──────────────────────────────────────────────

const CaptionCue = struct {
    text: []u8, // owned by the caller's allocator
    start: f64,
    end: f64,
};

// ── Internal: segment selection ────────────────────────────────────────

/// Returns [segments, word_mode] or null if no usable segments exist.
fn pickSegments(resp: *const TTSWithTimestampsResponse) ?struct { []const AlignmentSegmentWord, bool } {
    if (resp.words) |ws| {
        if (ws.len >= 2) return .{ ws, true };
    }
    if (resp.characters) |cs| {
        if (cs.len >= 1) return .{ cs, false };
    }
    // Single-entry words with no characters is still valid.
    if (resp.words) |ws| {
        if (ws.len == 1) {
            const no_chars = if (resp.characters) |cs| cs.len == 0 else true;
            if (no_chars) return .{ ws, true };
        }
    }
    return null;
}

// ── Internal: codepoint count ──────────────────────────────────────────

/// Count UTF-8 codepoints: every byte that is NOT a continuation byte.
fn utf8CodepointCount(s: []const u8) usize {
    var count: usize = 0;
    for (s) |b| {
        if ((b & 0xc0) != 0x80) count += 1;
    }
    return count;
}

// ── Internal: sentence terminator check ───────────────────────────────

/// Return true if `text` (after stripping trailing ASCII whitespace) ends with
/// one of the sentence-terminator sequences.
fn endsInSentence(text: []const u8) bool {
    var end = text.len;
    while (end > 0) {
        const c = text[end - 1];
        if (c == ' ' or c == '\t' or c == '\n' or c == '\r') {
            end -= 1;
        } else break;
    }
    const trimmed = text[0..end];
    for (&TERMINATORS) |term| {
        if (std.mem.endsWith(u8, trimmed, term)) return true;
    }
    return false;
}

// ── Internal: time formatters ──────────────────────────────────────────

/// Write HH:MM:SS,mmm (SRT format).
fn writeSrtTime(writer: *std.io.Writer, sec: f64) !void {
    const total_ms: u64 = @intFromFloat(@round(sec * 1000.0));
    const ms = total_ms % 1000;
    const s = (total_ms / 1000) % 60;
    const m = (total_ms / 60_000) % 60;
    const h = total_ms / 3_600_000;
    try writer.print("{d:0>2}:{d:0>2}:{d:0>2},{d:0>3}", .{ h, m, s, ms });
}

/// Write HH:MM:SS.mmm (VTT format).
fn writeVttTime(writer: *std.io.Writer, sec: f64) !void {
    const total_ms: u64 = @intFromFloat(@round(sec * 1000.0));
    const ms = total_ms % 1000;
    const s = (total_ms / 1000) % 60;
    const m = (total_ms / 60_000) % 60;
    const h = total_ms / 3_600_000;
    try writer.print("{d:0>2}:{d:0>2}:{d:0>2}.{d:0>3}", .{ h, m, s, ms });
}

// ── Internal: cue grouping ─────────────────────────────────────────────

/// Build caption cues from an alignment segment array.
///
/// word_mode = true  => parts joined with " "
/// word_mode = false => parts concatenated directly (char mode)
///
/// Caller must free each cue.text and then the returned slice.
///
/// TODO(TASK-12430-followup): expose max_seconds / max_chars override to match Python/JS API surface. Default 7.0s / 42 chars (BBC/Netflix guideline).
/// TODO(TASK-12430-followup): warn or error when alignment array contains majority-empty text segments — server contract should never produce these but defense-in-depth is desirable.
fn groupIntoCues(
    segs: []const AlignmentSegmentWord,
    word_mode: bool,
    allocator: Allocator,
) ![]CaptionCue {
    if (segs.len == 0) return &[_]CaptionCue{};

    var cues: std.ArrayList(CaptionCue) = .empty;
    errdefer {
        for (cues.items) |c| allocator.free(c.text);
        cues.deinit(allocator);
    }

    // Accumulate the current in-progress cue text.
    var cur: std.ArrayList(u8) = .empty;
    defer cur.deinit(allocator);

    var cur_start: f64 = 0.0;
    var last_end: f64 = 0.0;
    var has_cur: bool = false;

    for (segs) |seg| {
        if (has_cur) {
            // Compute codepoint count of the candidate (cur + separator + seg).
            const sep_len: usize = if (word_mode) 1 else 0;
            const candidate_cp =
                utf8CodepointCount(cur.items) +
                sep_len +
                utf8CodepointCount(seg.text);
            const would_exceed_sec = (seg.end - cur_start) > CAPTION_MAX_SECONDS;
            const would_exceed_chars = candidate_cp > CAPTION_MAX_CHARS;

            if (would_exceed_sec or would_exceed_chars) {
                // Flush current cue.
                const trimmed = std.mem.trim(u8, cur.items, " \t\n\r");
                if (trimmed.len > 0) {
                    try cues.append(allocator, .{
                        .text = try allocator.dupe(u8, trimmed),
                        .start = cur_start,
                        .end = last_end,
                    });
                }
                cur.clearRetainingCapacity();
                has_cur = false;
            }
        }

        if (!has_cur) {
            cur_start = seg.start;
            cur.clearRetainingCapacity();
            try cur.appendSlice(allocator, seg.text);
            has_cur = true;
        } else {
            if (word_mode) try cur.append(allocator, ' ');
            try cur.appendSlice(allocator, seg.text);
        }
        last_end = seg.end;

        if (endsInSentence(seg.text)) {
            // Flush on sentence terminator.
            const trimmed = std.mem.trim(u8, cur.items, " \t\n\r");
            if (trimmed.len > 0) {
                try cues.append(allocator, .{
                    .text = try allocator.dupe(u8, trimmed),
                    .start = cur_start,
                    .end = seg.end,
                });
            }
            cur.clearRetainingCapacity();
            has_cur = false;
        }
    }

    // Flush any remaining partial cue.
    if (has_cur and cur.items.len > 0) {
        const trimmed = std.mem.trim(u8, cur.items, " \t\n\r");
        if (trimmed.len > 0) {
            try cues.append(allocator, .{
                .text = try allocator.dupe(u8, trimmed),
                .start = cur_start,
                .end = last_end,
            });
        }
    }

    return cues.toOwnedSlice(allocator);
}

// ── JSON parsing for with-timestamps response ──────────────────────────

/// Parse the JSON body of a /v1/text-to-speech/with-timestamps response.
/// Returns a TTSWithTimestampsResponse whose memory is entirely owned by the
/// struct.  Call `deinit()` to release it.
pub fn parseWithTimestampsResponse(
    allocator: Allocator,
    data: []const u8,
) !TTSWithTimestampsResponse {
    const parsed = try std.json.parseFromSlice(std.json.Value, allocator, data, .{});
    defer parsed.deinit();

    const root = parsed.value.object;

    // audio (base64 string)
    const audio_b64: []const u8 = if (root.get("audio")) |v| switch (v) {
        .string => |s| s,
        else => return error.JsonParseError,
    } else return error.JsonParseError;

    // audio_format
    const audio_fmt: []const u8 = if (root.get("audio_format")) |v| switch (v) {
        .string => |s| s,
        else => return error.JsonParseError,
    } else return error.JsonParseError;

    // audio_duration
    const audio_dur: f64 = if (root.get("audio_duration")) |v| switch (v) {
        .float => |f| f,
        .integer => |n| @floatFromInt(n),
        else => return error.JsonParseError,
    } else 0.0;

    // words (optional array)
    const words: ?[]AlignmentSegmentWord = if (root.get("words")) |v| switch (v) {
        .array => |arr| try parseSegmentArray(allocator, arr.items),
        .null => null,
        else => null,
    } else null;

    // characters (optional array)
    const characters: ?[]AlignmentSegmentCharacter = if (root.get("characters")) |v| switch (v) {
        .array => |arr| try parseSegmentArray(allocator, arr.items),
        .null => null,
        else => null,
    } else null;

    return TTSWithTimestampsResponse{
        .audio_base64 = try allocator.dupe(u8, audio_b64),
        .audio_format = try allocator.dupe(u8, audio_fmt),
        .audio_duration = audio_dur,
        .words = words,
        .characters = characters,
        .allocator = allocator,
    };
}

fn parseSegmentArray(
    allocator: Allocator,
    items: []std.json.Value,
) ![]AlignmentSegmentWord {
    const segs = try allocator.alloc(AlignmentSegmentWord, items.len);
    var initialized: usize = 0;
    errdefer {
        for (segs[0..initialized]) |s| allocator.free(s.text);
        allocator.free(segs);
    }

    for (items, 0..) |item, i| {
        const obj = switch (item) {
            .object => |o| o,
            else => return error.JsonParseError,
        };
        const text: []const u8 = if (obj.get("text")) |v| switch (v) {
            .string => |s| s,
            else => return error.JsonParseError,
        } else return error.JsonParseError;

        const start: f64 = if (obj.get("start")) |v| switch (v) {
            .float => |f| f,
            .integer => |n| @floatFromInt(n),
            else => return error.JsonParseError,
        } else return error.JsonParseError;

        const end_t: f64 = if (obj.get("end")) |v| switch (v) {
            .float => |f| f,
            .integer => |n| @floatFromInt(n),
            else => return error.JsonParseError,
        } else return error.JsonParseError;

        segs[i] = .{
            .text = try allocator.dupe(u8, text),
            .start = start,
            .end = end_t,
        };
        initialized = i + 1;
    }

    return segs;
}

// ── JSON serialization for with-timestamps request ─────────────────────

/// Serialize a TTSRequestWithTimestamps to JSON bytes.
/// Caller owns the returned slice.
pub fn serializeTtsRequestWithTimestamps(
    allocator: Allocator,
    req: TTSRequestWithTimestamps,
    granularity: ?[]const u8,
) ![]u8 {
    // Build the base TTS request JSON, then splice in granularity if provided.
    const base_req = models.TtsRequest{
        .voice_id = req.voice_id,
        .text = req.text,
        .model = req.model,
        .language = req.language,
        .prompt = req.prompt,
        .output = req.output,
        .seed = req.seed,
    };
    const base_json = try json_helpers.serializeTtsRequest(allocator, base_req);
    defer allocator.free(base_json);

    const gran = granularity orelse return allocator.dupe(u8, base_json);
    if (gran.len == 0) return allocator.dupe(u8, base_json);

    // base_json ends with '}'; inject granularity before the closing brace.
    if (base_json.len == 0 or base_json[base_json.len - 1] != '}') {
        return error.JsonSerializeError;
    }

    var aw: std.io.Writer.Allocating = .init(allocator);
    errdefer aw.deinit();

    // Everything except the trailing '}'
    try aw.writer.writeAll(base_json[0 .. base_json.len - 1]);
    try aw.writer.writeAll(",\"granularity\":");
    // Encode granularity as a JSON string (simple ASCII — no escaping needed).
    try aw.writer.writeByte('"');
    try aw.writer.writeAll(gran);
    try aw.writer.writeByte('"');
    try aw.writer.writeByte('}');
    try aw.writer.flush();

    return aw.toOwnedSlice();
}
