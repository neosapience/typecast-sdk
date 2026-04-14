const std = @import("std");
const testing = std.testing;
const models = @import("typecast").models;
const json_helpers = @import("typecast").json_helpers;

// ── TtsModel ───────────────────────────────────────────────────────────

test "TtsModel toString" {
    try testing.expectEqualStrings("ssfm-v21", models.TtsModel.ssfm_v21.toString());
    try testing.expectEqualStrings("ssfm-v30", models.TtsModel.ssfm_v30.toString());
}

test "TtsModel fromString" {
    try testing.expectEqual(models.TtsModel.ssfm_v21, models.TtsModel.fromString("ssfm-v21").?);
    try testing.expectEqual(models.TtsModel.ssfm_v30, models.TtsModel.fromString("ssfm-v30").?);
    try testing.expectEqual(@as(?models.TtsModel, null), models.TtsModel.fromString("invalid"));
}

// ── AudioFormat ────────────────────────────────────────────────────────

test "AudioFormat toString" {
    try testing.expectEqualStrings("wav", models.AudioFormat.wav.toString());
    try testing.expectEqualStrings("mp3", models.AudioFormat.mp3.toString());
}

test "AudioFormat fromString" {
    try testing.expectEqual(models.AudioFormat.wav, models.AudioFormat.fromString("wav").?);
    try testing.expectEqual(models.AudioFormat.mp3, models.AudioFormat.fromString("mp3").?);
    try testing.expectEqual(@as(?models.AudioFormat, null), models.AudioFormat.fromString("ogg"));
}

// ── EmotionPreset ──────────────────────────────────────────────────────

test "EmotionPreset toString" {
    try testing.expectEqualStrings("normal", models.EmotionPreset.normal.toString());
    try testing.expectEqualStrings("happy", models.EmotionPreset.happy.toString());
    try testing.expectEqualStrings("sad", models.EmotionPreset.sad.toString());
    try testing.expectEqualStrings("angry", models.EmotionPreset.angry.toString());
    try testing.expectEqualStrings("whisper", models.EmotionPreset.whisper.toString());
    try testing.expectEqualStrings("toneup", models.EmotionPreset.toneup.toString());
    try testing.expectEqualStrings("tonedown", models.EmotionPreset.tonedown.toString());
}

test "EmotionPreset fromString" {
    try testing.expectEqual(models.EmotionPreset.normal, models.EmotionPreset.fromString("normal").?);
    try testing.expectEqual(models.EmotionPreset.happy, models.EmotionPreset.fromString("happy").?);
    try testing.expectEqual(models.EmotionPreset.angry, models.EmotionPreset.fromString("angry").?);
    try testing.expectEqual(@as(?models.EmotionPreset, null), models.EmotionPreset.fromString("unknown"));
}

// ── Gender ─────────────────────────────────────────────────────────────

test "Gender toString" {
    try testing.expectEqualStrings("male", models.Gender.male.toString());
    try testing.expectEqualStrings("female", models.Gender.female.toString());
}

test "Gender fromString" {
    try testing.expectEqual(models.Gender.male, models.Gender.fromString("male").?);
    try testing.expectEqual(models.Gender.female, models.Gender.fromString("female").?);
    try testing.expectEqual(@as(?models.Gender, null), models.Gender.fromString("other"));
}

// ── Age ────────────────────────────────────────────────────────────────

test "Age toString" {
    try testing.expectEqualStrings("child", models.Age.child.toString());
    try testing.expectEqualStrings("teenager", models.Age.teenager.toString());
    try testing.expectEqualStrings("young_adult", models.Age.young_adult.toString());
    try testing.expectEqualStrings("middle_age", models.Age.middle_age.toString());
    try testing.expectEqualStrings("elder", models.Age.elder.toString());
}

test "Age fromString" {
    try testing.expectEqual(models.Age.child, models.Age.fromString("child").?);
    try testing.expectEqual(models.Age.elder, models.Age.fromString("elder").?);
    try testing.expectEqual(@as(?models.Age, null), models.Age.fromString("baby"));
}

// ── PlanTier ───────────────────────────────────────────────────────────

test "PlanTier toString" {
    try testing.expectEqualStrings("free", models.PlanTier.free.toString());
    try testing.expectEqualStrings("lite", models.PlanTier.lite.toString());
    try testing.expectEqualStrings("plus", models.PlanTier.plus.toString());
    try testing.expectEqualStrings("custom", models.PlanTier.custom.toString());
}

test "PlanTier fromString" {
    try testing.expectEqual(models.PlanTier.free, models.PlanTier.fromString("free").?);
    try testing.expectEqual(models.PlanTier.custom, models.PlanTier.fromString("custom").?);
    try testing.expectEqual(@as(?models.PlanTier, null), models.PlanTier.fromString("enterprise"));
}

// ── Output defaults ────────────────────────────────────────────────────

test "Output default values" {
    const output = models.Output{};
    try testing.expectEqual(@as(?i32, 100), output.volume);
    try testing.expectEqual(@as(?f64, null), output.target_lufs);
    try testing.expectEqual(@as(?i32, 0), output.audio_pitch);
    try testing.expectEqual(@as(?f64, 1.0), output.audio_tempo);
    try testing.expectEqual(models.AudioFormat.wav, output.audio_format);
}

// ── OutputStream defaults and field checks ─────────────────────────────

test "OutputStream default values" {
    const output = models.OutputStream{};
    try testing.expectEqual(@as(?i32, 0), output.audio_pitch);
    try testing.expectEqual(@as(?f64, 1.0), output.audio_tempo);
    try testing.expectEqual(models.AudioFormat.wav, output.audio_format);
}

test "OutputStream must not have volume or target_lufs" {
    // Compile-time check: these fields must not exist on OutputStream.
    try testing.expect(!@hasField(models.OutputStream, "volume"));
    try testing.expect(!@hasField(models.OutputStream, "target_lufs"));
}

// ── TtsRequest required vs optional fields ─────────────────────────────

test "TtsRequest required and optional fields" {
    const req = models.TtsRequest{
        .voice_id = "voice-abc",
        .text = "Hello",
        .model = .ssfm_v30,
    };
    try testing.expectEqualStrings("voice-abc", req.voice_id);
    try testing.expectEqualStrings("Hello", req.text);
    try testing.expectEqual(models.TtsModel.ssfm_v30, req.model);
    // Optional fields default to null
    try testing.expectEqual(@as(?[]const u8, null), req.language);
    try testing.expectEqual(@as(?models.TtsPrompt, null), req.prompt);
    try testing.expectEqual(@as(?models.Output, null), req.output);
    try testing.expectEqual(@as(?i64, null), req.seed);
}

test "TtsRequestStream uses OutputStream" {
    const req = models.TtsRequestStream{
        .voice_id = "v1",
        .text = "Hi",
        .model = .ssfm_v21,
        .output = models.OutputStream{ .audio_format = .mp3 },
    };
    try testing.expectEqual(models.AudioFormat.mp3, req.output.?.audio_format);
}

// ── TtsPrompt union variants ───────────────────────────────────────────

test "TtsPrompt smart variant" {
    const prompt = models.TtsPrompt{
        .smart = models.SmartPrompt{
            .previous_text = "before",
            .next_text = "after",
        },
    };
    switch (prompt) {
        .smart => |s| {
            try testing.expectEqualStrings("before", s.previous_text.?);
            try testing.expectEqualStrings("after", s.next_text.?);
        },
        else => return error.TestUnexpectedResult,
    }
}

test "TtsPrompt preset variant" {
    const prompt = models.TtsPrompt{
        .preset = models.PresetPrompt{
            .emotion_preset = .happy,
            .emotion_intensity = 0.8,
        },
    };
    switch (prompt) {
        .preset => |p| {
            try testing.expectEqual(models.EmotionPreset.happy, p.emotion_preset);
            try testing.expectEqual(@as(?f64, 0.8), p.emotion_intensity);
        },
        else => return error.TestUnexpectedResult,
    }
}

test "TtsPrompt basic variant" {
    const prompt = models.TtsPrompt{
        .basic = models.Prompt{},
    };
    switch (prompt) {
        .basic => |b| {
            try testing.expectEqual(models.EmotionPreset.normal, b.emotion_preset);
            try testing.expectEqual(@as(?f64, 1.0), b.emotion_intensity);
        },
        else => return error.TestUnexpectedResult,
    }
}

// ── SmartPrompt defaults ───────────────────────────────────────────────

test "SmartPrompt defaults to null" {
    const sp = models.SmartPrompt{};
    try testing.expectEqual(@as(?[]const u8, null), sp.previous_text);
    try testing.expectEqual(@as(?[]const u8, null), sp.next_text);
}

// ── PresetPrompt defaults ──────────────────────────────────────────────

test "PresetPrompt defaults" {
    const pp = models.PresetPrompt{};
    try testing.expectEqual(models.EmotionPreset.normal, pp.emotion_preset);
    try testing.expectEqual(@as(?f64, 1.0), pp.emotion_intensity);
}

// ── JSON Serialization Tests ──────────────────────────────────────────

test "serializeTtsRequest basic fields" {
    const allocator = testing.allocator;
    const req = models.TtsRequest{
        .voice_id = "voice-123",
        .text = "Hello world",
        .model = .ssfm_v30,
    };
    const json_bytes = try json_helpers.serializeTtsRequest(allocator, req);
    defer allocator.free(json_bytes);

    // Parse back to verify
    const parsed = try std.json.parseFromSlice(std.json.Value, allocator, json_bytes, .{});
    defer parsed.deinit();
    const obj = parsed.value.object;

    try testing.expectEqualStrings("Hello world", obj.get("text").?.string);
    try testing.expectEqualStrings("voice-123", obj.get("voice_id").?.string);
    try testing.expectEqualStrings("ssfm-v30", obj.get("model").?.string);
    // Optional fields should not be present
    try testing.expect(obj.get("language") == null);
    try testing.expect(obj.get("seed") == null);
    try testing.expect(obj.get("prompt") == null);
    try testing.expect(obj.get("output") == null);
}

test "serializeTtsRequest with all optional fields" {
    const allocator = testing.allocator;
    const req = models.TtsRequest{
        .voice_id = "v1",
        .text = "Test",
        .model = .ssfm_v21,
        .language = "en",
        .seed = 42,
        .prompt = .{ .basic = .{ .emotion_preset = .happy, .emotion_intensity = 0.8 } },
        .output = .{ .volume = 80, .audio_pitch = 2, .audio_tempo = 1.5, .audio_format = .mp3 },
    };
    const json_bytes = try json_helpers.serializeTtsRequest(allocator, req);
    defer allocator.free(json_bytes);

    const parsed = try std.json.parseFromSlice(std.json.Value, allocator, json_bytes, .{});
    defer parsed.deinit();
    const obj = parsed.value.object;

    try testing.expectEqualStrings("en", obj.get("language").?.string);
    try testing.expectEqual(@as(i64, 42), obj.get("seed").?.integer);
    try testing.expect(obj.get("prompt") != null);
    try testing.expect(obj.get("output") != null);
}

test "serializeTtsRequestStream has no volume or target_lufs" {
    const allocator = testing.allocator;
    const req = models.TtsRequestStream{
        .voice_id = "v1",
        .text = "Stream test",
        .model = .ssfm_v30,
        .output = .{ .audio_format = .mp3 },
    };
    const json_bytes = try json_helpers.serializeTtsRequestStream(allocator, req);
    defer allocator.free(json_bytes);

    const parsed = try std.json.parseFromSlice(std.json.Value, allocator, json_bytes, .{});
    defer parsed.deinit();
    const output_obj = parsed.value.object.get("output").?.object;

    try testing.expect(output_obj.get("volume") == null);
    try testing.expect(output_obj.get("target_lufs") == null);
    try testing.expectEqualStrings("mp3", output_obj.get("audio_format").?.string);
}

test "serialize SmartPrompt" {
    const allocator = testing.allocator;
    const req = models.TtsRequest{
        .voice_id = "v1",
        .text = "hi",
        .model = .ssfm_v30,
        .prompt = .{ .smart = .{ .previous_text = "before", .next_text = "after" } },
    };
    const json_bytes = try json_helpers.serializeTtsRequest(allocator, req);
    defer allocator.free(json_bytes);

    const parsed = try std.json.parseFromSlice(std.json.Value, allocator, json_bytes, .{});
    defer parsed.deinit();
    const prompt_obj = parsed.value.object.get("prompt").?.object;

    try testing.expectEqualStrings("smart", prompt_obj.get("emotion_type").?.string);
    try testing.expectEqualStrings("before", prompt_obj.get("previous_text").?.string);
    try testing.expectEqualStrings("after", prompt_obj.get("next_text").?.string);
}

test "serialize PresetPrompt" {
    const allocator = testing.allocator;
    const req = models.TtsRequest{
        .voice_id = "v1",
        .text = "hi",
        .model = .ssfm_v30,
        .prompt = .{ .preset = .{ .emotion_preset = .sad, .emotion_intensity = 0.5 } },
    };
    const json_bytes = try json_helpers.serializeTtsRequest(allocator, req);
    defer allocator.free(json_bytes);

    const parsed = try std.json.parseFromSlice(std.json.Value, allocator, json_bytes, .{});
    defer parsed.deinit();
    const prompt_obj = parsed.value.object.get("prompt").?.object;

    try testing.expectEqualStrings("preset", prompt_obj.get("emotion_type").?.string);
    try testing.expectEqualStrings("sad", prompt_obj.get("emotion_preset").?.string);
}

test "serialize basic Prompt" {
    const allocator = testing.allocator;
    const req = models.TtsRequest{
        .voice_id = "v1",
        .text = "hi",
        .model = .ssfm_v30,
        .prompt = .{ .basic = .{ .emotion_preset = .angry, .emotion_intensity = 1.0 } },
    };
    const json_bytes = try json_helpers.serializeTtsRequest(allocator, req);
    defer allocator.free(json_bytes);

    const parsed = try std.json.parseFromSlice(std.json.Value, allocator, json_bytes, .{});
    defer parsed.deinit();
    const prompt_obj = parsed.value.object.get("prompt").?.object;

    // Basic prompt should NOT have emotion_type field
    try testing.expect(prompt_obj.get("emotion_type") == null);
    try testing.expectEqualStrings("angry", prompt_obj.get("emotion_preset").?.string);
}

// ── JSON Deserialization Tests ────────────────────────────────────────

test "parseSubscriptionResponse" {
    const allocator = testing.allocator;
    const json_str =
        \\{"plan_tier":"plus","credits":{"total":10000,"used":2500,"remaining":7500},"limits":{"max_text_length":5000,"max_requests_per_minute":60}}
    ;
    const sub = try json_helpers.parseSubscriptionResponse(allocator, json_str);

    try testing.expectEqual(models.PlanTier.plus, sub.plan_tier);
    try testing.expectEqual(@as(i64, 10000), sub.credits.total);
    try testing.expectEqual(@as(i64, 2500), sub.credits.used);
    try testing.expectEqual(@as(i64, 7500), sub.credits.remaining);
    try testing.expectEqual(@as(i32, 5000), sub.limits.max_text_length);
    try testing.expectEqual(@as(i32, 60), sub.limits.max_requests_per_minute);
}

test "parseErrorDetail with detail field" {
    const result = json_helpers.parseErrorDetail(
        \\{"detail":"error msg"}
    );
    try testing.expect(result != null);
    try testing.expectEqualStrings("error msg", result.?);
}

test "parseErrorDetail with message field" {
    const result = json_helpers.parseErrorDetail(
        \\{"message":"something went wrong"}
    );
    try testing.expect(result != null);
    try testing.expectEqualStrings("something went wrong", result.?);
}

test "parseErrorDetail with invalid JSON" {
    const result = json_helpers.parseErrorDetail("not json");
    try testing.expect(result == null);
}
