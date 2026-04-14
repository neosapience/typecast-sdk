const std = @import("std");
const models = @import("models.zig");

// ── Serialization ─────────────────────────────────────────────────────

/// Serialize a TtsRequest to JSON bytes. Caller owns returned memory.
pub fn serializeTtsRequest(allocator: std.mem.Allocator, req: models.TtsRequest) ![]u8 {
    var aw: std.io.Writer.Allocating = .init(allocator);
    errdefer aw.deinit();
    var ws = std.json.Stringify{ .writer = &aw.writer };

    try ws.beginObject();

    try ws.objectField("text");
    try ws.write(req.text);

    try ws.objectField("voice_id");
    try ws.write(req.voice_id);

    try ws.objectField("model");
    try ws.write(req.model.toString());

    if (req.language) |lang| {
        try ws.objectField("language");
        try ws.write(lang);
    }

    if (req.seed) |seed| {
        try ws.objectField("seed");
        try ws.write(seed);
    }

    if (req.prompt) |prompt| {
        try ws.objectField("prompt");
        try writePrompt(&ws, prompt);
    }

    if (req.output) |output| {
        try ws.objectField("output");
        try writeOutput(&ws, output);
    }

    try ws.endObject();
    try aw.writer.flush();

    return aw.toOwnedSlice();
}

/// Serialize a TtsRequestStream to JSON bytes. Caller owns returned memory.
pub fn serializeTtsRequestStream(allocator: std.mem.Allocator, req: models.TtsRequestStream) ![]u8 {
    var aw: std.io.Writer.Allocating = .init(allocator);
    errdefer aw.deinit();
    var ws = std.json.Stringify{ .writer = &aw.writer };

    try ws.beginObject();

    try ws.objectField("text");
    try ws.write(req.text);

    try ws.objectField("voice_id");
    try ws.write(req.voice_id);

    try ws.objectField("model");
    try ws.write(req.model.toString());

    if (req.language) |lang| {
        try ws.objectField("language");
        try ws.write(lang);
    }

    if (req.seed) |seed| {
        try ws.objectField("seed");
        try ws.write(seed);
    }

    if (req.prompt) |prompt| {
        try ws.objectField("prompt");
        try writePrompt(&ws, prompt);
    }

    if (req.output) |output| {
        try ws.objectField("output");
        try writeOutputStream(&ws, output);
    }

    try ws.endObject();
    try aw.writer.flush();

    return aw.toOwnedSlice();
}

fn writePrompt(ws: *std.json.Stringify, prompt: models.TtsPrompt) !void {
    try ws.beginObject();
    switch (prompt) {
        .smart => |s| {
            try ws.objectField("emotion_type");
            try ws.write("smart");
            if (s.previous_text) |pt| {
                try ws.objectField("previous_text");
                try ws.write(pt);
            }
            if (s.next_text) |nt| {
                try ws.objectField("next_text");
                try ws.write(nt);
            }
        },
        .preset => |p| {
            try ws.objectField("emotion_type");
            try ws.write("preset");
            try ws.objectField("emotion_preset");
            try ws.write(p.emotion_preset.toString());
            if (p.emotion_intensity) |ei| {
                try ws.objectField("emotion_intensity");
                try ws.write(ei);
            }
        },
        .basic => |b| {
            try ws.objectField("emotion_preset");
            try ws.write(b.emotion_preset.toString());
            if (b.emotion_intensity) |ei| {
                try ws.objectField("emotion_intensity");
                try ws.write(ei);
            }
        },
    }
    try ws.endObject();
}

fn writeOutput(ws: *std.json.Stringify, output: models.Output) !void {
    try ws.beginObject();

    // If target_lufs is set, use it; otherwise use volume
    if (output.target_lufs) |lufs| {
        try ws.objectField("target_lufs");
        try ws.write(lufs);
    } else if (output.volume) |vol| {
        try ws.objectField("volume");
        try ws.write(vol);
    }

    if (output.audio_pitch) |pitch| {
        if (pitch != 0) {
            try ws.objectField("audio_pitch");
            try ws.write(pitch);
        }
    }

    if (output.audio_tempo) |tempo| {
        if (tempo != 0.0 and tempo != 1.0) {
            try ws.objectField("audio_tempo");
            try ws.write(tempo);
        }
    }

    try ws.objectField("audio_format");
    try ws.write(output.audio_format.toString());

    try ws.endObject();
}

fn writeOutputStream(ws: *std.json.Stringify, output: models.OutputStream) !void {
    try ws.beginObject();

    if (output.audio_pitch) |pitch| {
        if (pitch != 0) {
            try ws.objectField("audio_pitch");
            try ws.write(pitch);
        }
    }

    if (output.audio_tempo) |tempo| {
        if (tempo != 0.0 and tempo != 1.0) {
            try ws.objectField("audio_tempo");
            try ws.write(tempo);
        }
    }

    try ws.objectField("audio_format");
    try ws.write(output.audio_format.toString());

    try ws.endObject();
}

// ── Deserialization ───────────────────────────────────────────────────

/// Parse a SubscriptionResponse from JSON bytes.
pub fn parseSubscriptionResponse(allocator: std.mem.Allocator, data: []const u8) !models.SubscriptionResponse {
    const parsed = try std.json.parseFromSlice(std.json.Value, allocator, data, .{});
    defer parsed.deinit();
    const root = parsed.value;

    const plan_str = root.object.get("plan").?.string;
    const credits_obj = root.object.get("credits").?.object;
    const limits_obj = root.object.get("limits").?.object;

    return models.SubscriptionResponse{
        .plan = try allocator.dupe(u8, plan_str),
        .credits = .{
            .plan_credits = credits_obj.get("plan_credits").?.integer,
            .used_credits = credits_obj.get("used_credits").?.integer,
        },
        .limits = .{
            .concurrency_limit = @intCast(limits_obj.get("concurrency_limit").?.integer),
        },
    };
}

/// Parse an array of Voice from JSON bytes. Caller owns returned slice.
pub fn parseVoices(allocator: std.mem.Allocator, data: []const u8) ![]models.Voice {
    const parsed = try std.json.parseFromSlice(std.json.Value, allocator, data, .{});
    defer parsed.deinit();
    const items = parsed.value.array.items;

    var voices = try allocator.alloc(models.Voice, items.len);
    var initialized: usize = 0;
    errdefer {
        for (voices[0..initialized]) |v| {
            allocator.free(v.voice_id);
            allocator.free(v.voice_name);
            for (v.emotions) |e| allocator.free(e);
            allocator.free(v.emotions);
        }
        allocator.free(voices);
    }

    for (items, 0..) |item, i| {
        const obj = item.object;
        const emotions_arr = obj.get("emotions").?.array.items;
        const emotions = try allocator.alloc([]const u8, emotions_arr.len);
        for (emotions_arr, 0..) |e, j| {
            emotions[j] = try allocator.dupe(u8, e.string);
        }
        voices[i] = .{
            .voice_id = try allocator.dupe(u8, obj.get("voice_id").?.string),
            .voice_name = try allocator.dupe(u8, obj.get("voice_name").?.string),
            .model = models.TtsModel.fromString(obj.get("model").?.string) orelse return error.InvalidModel,
            .emotions = emotions,
        };
        initialized = i + 1;
    }

    return voices;
}

/// Parse an array of VoiceV2 from JSON bytes. Caller owns returned slice.
pub fn parseVoicesV2(allocator: std.mem.Allocator, data: []const u8) ![]models.VoiceV2 {
    const parsed = try std.json.parseFromSlice(std.json.Value, allocator, data, .{});
    defer parsed.deinit();
    const items = parsed.value.array.items;

    var voices = try allocator.alloc(models.VoiceV2, items.len);
    var initialized: usize = 0;
    errdefer {
        for (voices[0..initialized]) |v| {
            allocator.free(v.voice_id);
            allocator.free(v.voice_name);
            for (v.models) |mi| {
                allocator.free(mi.version);
                for (mi.emotions) |e| allocator.free(e);
                allocator.free(mi.emotions);
            }
            allocator.free(v.models);
            if (v.use_cases) |cases| {
                for (cases) |c| allocator.free(c);
                allocator.free(cases);
            }
        }
        allocator.free(voices);
    }

    for (items, 0..) |item, i| {
        const obj = item.object;

        // Parse models array
        const models_arr = obj.get("models").?.array.items;
        const model_infos = try allocator.alloc(models.ModelInfo, models_arr.len);
        for (models_arr, 0..) |m, mi| {
            const mobj = m.object;
            const emo_arr = mobj.get("emotions").?.array.items;
            const emos = try allocator.alloc([]const u8, emo_arr.len);
            for (emo_arr, 0..) |e, ei| {
                emos[ei] = try allocator.dupe(u8, e.string);
            }
            model_infos[mi] = .{
                .version = try allocator.dupe(u8, mobj.get("version").?.string),
                .emotions = emos,
            };
        }

        // Parse optional gender
        const gender: ?models.Gender = if (obj.get("gender")) |g| switch (g) {
            .string => |s| models.Gender.fromString(s),
            else => null,
        } else null;

        // Parse optional age
        const age: ?models.Age = if (obj.get("age")) |a| switch (a) {
            .string => |s| models.Age.fromString(s),
            else => null,
        } else null;

        // Parse optional use_cases
        const use_cases: ?[]const []const u8 = if (obj.get("use_cases")) |uc| switch (uc) {
            .array => |arr| blk: {
                const cases = try allocator.alloc([]const u8, arr.items.len);
                for (arr.items, 0..) |c, ci| {
                    cases[ci] = try allocator.dupe(u8, c.string);
                }
                break :blk cases;
            },
            else => null,
        } else null;

        voices[i] = .{
            .voice_id = try allocator.dupe(u8, obj.get("voice_id").?.string),
            .voice_name = try allocator.dupe(u8, obj.get("voice_name").?.string),
            .models = model_infos,
            .gender = gender,
            .age = age,
            .use_cases = use_cases,
        };
        initialized = i + 1;
    }

    return voices;
}

/// Extract error detail message from error JSON response.
/// Returns null if parsing fails or neither "detail" nor "message" is found.
/// The returned slice is backed by page_allocator and intentionally not freed
/// since this is used only in error paths for diagnostic messages.
pub fn parseErrorDetail(data: []const u8) ?[]const u8 {
    const parsed = std.json.parseFromSlice(
        std.json.Value,
        std.heap.page_allocator,
        data,
        .{},
    ) catch return null;
    // Intentionally not calling parsed.deinit() so the returned string
    // slice remains valid. This leaks memory but is acceptable since this
    // function is only called on error paths.
    const root = parsed.value;

    if (root.object.get("detail")) |d| {
        switch (d) {
            .string => |s| return s,
            else => {},
        }
    }
    if (root.object.get("message")) |m| {
        switch (m) {
            .string => |s| return s,
            else => {},
        }
    }
    // No detail found, we can safely deinit
    parsed.deinit();
    return null;
}
