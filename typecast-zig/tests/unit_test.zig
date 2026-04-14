const std = @import("std");
const testing = std.testing;
const models = @import("typecast").models;

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
