const std = @import("std");

// ── Enums ──────────────────────────────────────────────────────────────

pub const TtsModel = enum {
    ssfm_v21,
    ssfm_v30,

    const Self = @This();

    pub fn toString(self: Self) []const u8 {
        return switch (self) {
            .ssfm_v21 => "ssfm-v21",
            .ssfm_v30 => "ssfm-v30",
        };
    }

    pub fn fromString(s: []const u8) ?Self {
        if (std.mem.eql(u8, s, "ssfm-v21")) return .ssfm_v21;
        if (std.mem.eql(u8, s, "ssfm-v30")) return .ssfm_v30;
        return null;
    }
};

pub const AudioFormat = enum {
    wav,
    mp3,

    const Self = @This();

    pub fn toString(self: Self) []const u8 {
        return switch (self) {
            .wav => "wav",
            .mp3 => "mp3",
        };
    }

    pub fn fromString(s: []const u8) ?Self {
        if (std.mem.eql(u8, s, "wav")) return .wav;
        if (std.mem.eql(u8, s, "mp3")) return .mp3;
        return null;
    }
};

pub const EmotionPreset = enum {
    normal,
    happy,
    sad,
    angry,
    whisper,
    toneup,
    tonedown,

    const Self = @This();

    pub fn toString(self: Self) []const u8 {
        return switch (self) {
            .normal => "normal",
            .happy => "happy",
            .sad => "sad",
            .angry => "angry",
            .whisper => "whisper",
            .toneup => "toneup",
            .tonedown => "tonedown",
        };
    }

    pub fn fromString(s: []const u8) ?Self {
        if (std.mem.eql(u8, s, "normal")) return .normal;
        if (std.mem.eql(u8, s, "happy")) return .happy;
        if (std.mem.eql(u8, s, "sad")) return .sad;
        if (std.mem.eql(u8, s, "angry")) return .angry;
        if (std.mem.eql(u8, s, "whisper")) return .whisper;
        if (std.mem.eql(u8, s, "toneup")) return .toneup;
        if (std.mem.eql(u8, s, "tonedown")) return .tonedown;
        return null;
    }
};

pub const Gender = enum {
    male,
    female,

    const Self = @This();

    pub fn toString(self: Self) []const u8 {
        return switch (self) {
            .male => "male",
            .female => "female",
        };
    }

    pub fn fromString(s: []const u8) ?Self {
        if (std.mem.eql(u8, s, "male")) return .male;
        if (std.mem.eql(u8, s, "female")) return .female;
        return null;
    }
};

pub const Age = enum {
    child,
    teenager,
    young_adult,
    middle_age,
    elder,

    const Self = @This();

    pub fn toString(self: Self) []const u8 {
        return switch (self) {
            .child => "child",
            .teenager => "teenager",
            .young_adult => "young_adult",
            .middle_age => "middle_age",
            .elder => "elder",
        };
    }

    pub fn fromString(s: []const u8) ?Self {
        if (std.mem.eql(u8, s, "child")) return .child;
        if (std.mem.eql(u8, s, "teenager")) return .teenager;
        if (std.mem.eql(u8, s, "young_adult")) return .young_adult;
        if (std.mem.eql(u8, s, "middle_age")) return .middle_age;
        if (std.mem.eql(u8, s, "elder")) return .elder;
        return null;
    }
};

pub const PlanTier = enum {
    free,
    lite,
    plus,
    custom,

    const Self = @This();

    pub fn toString(self: Self) []const u8 {
        return switch (self) {
            .free => "free",
            .lite => "lite",
            .plus => "plus",
            .custom => "custom",
        };
    }

    pub fn fromString(s: []const u8) ?Self {
        if (std.mem.eql(u8, s, "free")) return .free;
        if (std.mem.eql(u8, s, "lite")) return .lite;
        if (std.mem.eql(u8, s, "plus")) return .plus;
        if (std.mem.eql(u8, s, "custom")) return .custom;
        return null;
    }
};

// ── Prompt types ───────────────────────────────────────────────────────

pub const SmartPrompt = struct {
    previous_text: ?[]const u8 = null,
    next_text: ?[]const u8 = null,
};

pub const PresetPrompt = struct {
    emotion_preset: EmotionPreset = .normal,
    emotion_intensity: ?f64 = 1.0,
};

pub const Prompt = struct {
    emotion_preset: EmotionPreset = .normal,
    emotion_intensity: ?f64 = 1.0,
};

pub const TtsPrompt = union(enum) {
    smart: SmartPrompt,
    preset: PresetPrompt,
    basic: Prompt,
};

// ── Output types ───────────────────────────────────────────────────────

pub const Output = struct {
    volume: ?i32 = 100,
    target_lufs: ?f64 = null,
    audio_pitch: ?i32 = 0,
    audio_tempo: ?f64 = 1.0,
    audio_format: AudioFormat = .wav,
};

pub const OutputStream = struct {
    audio_pitch: ?i32 = 0,
    audio_tempo: ?f64 = 1.0,
    audio_format: AudioFormat = .wav,
};

// ── Request types ──────────────────────────────────────────────────────

pub const TtsRequest = struct {
    voice_id: []const u8,
    text: []const u8,
    model: TtsModel,
    language: ?[]const u8 = null,
    prompt: ?TtsPrompt = null,
    output: ?Output = null,
    seed: ?i64 = null,
};

pub const TtsRequestStream = struct {
    voice_id: []const u8,
    text: []const u8,
    model: TtsModel,
    language: ?[]const u8 = null,
    prompt: ?TtsPrompt = null,
    output: ?OutputStream = null,
    seed: ?i64 = null,
};

// ── Response types ─────────────────────────────────────────────────────

pub const TtsResponse = struct {
    audio_data: []const u8,
    duration: f64,
    format: AudioFormat,
};

pub const SubscriptionResponse = struct {
    pub const Credits = struct {
        total: i64,
        used: i64,
        remaining: i64,
    };

    pub const Limits = struct {
        max_text_length: i32,
        max_requests_per_minute: i32,
    };

    plan_tier: PlanTier,
    credits: Credits,
    limits: Limits,
};

// ── Voice types ────────────────────────────────────────────────────────

pub const Voice = struct {
    voice_id: []const u8,
    voice_name: []const u8,
    model: TtsModel,
    emotions: []const []const u8,
};

pub const ModelInfo = struct {
    version: []const u8,
    emotions: []const []const u8,
};

pub const VoiceV2 = struct {
    voice_id: []const u8,
    voice_name: []const u8,
    models: []const ModelInfo,
    gender: ?Gender = null,
    age: ?Age = null,
    use_cases: ?[]const []const u8 = null,
};

pub const VoicesV2Filter = struct {
    model: ?TtsModel = null,
    gender: ?Gender = null,
    age: ?Age = null,
    use_cases: ?[]const u8 = null,
};
