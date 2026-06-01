const std = @import("std");

pub fn build(b: *std.Build) void {
    const target = b.standardTargetOptions(.{});
    const optimize = b.standardOptimizeOption(.{});

    // Library module
    const typecast_mod = b.addModule("typecast", .{
        .root_source_file = b.path("src/root.zig"),
        .target = target,
        .optimize = optimize,
    });

    // Unit tests
    const unit_test_mod = b.createModule(.{
        .root_source_file = b.path("tests/unit_test.zig"),
        .target = target,
        .optimize = optimize,
        .imports = &.{.{ .name = "typecast", .module = typecast_mod }},
    });
    const unit_tests = b.addTest(.{ .root_module = unit_test_mod });
    const run_unit_tests = b.addRunArtifact(unit_tests);
    const test_step = b.step("test", "Run unit tests");
    test_step.dependOn(&run_unit_tests.step);

    // Mock tests
    const mock_test_mod = b.createModule(.{
        .root_source_file = b.path("tests/mock_test.zig"),
        .target = target,
        .optimize = optimize,
        .imports = &.{.{ .name = "typecast", .module = typecast_mod }},
    });
    const mock_tests = b.addTest(.{ .root_module = mock_test_mod });
    const run_mock_tests = b.addRunArtifact(mock_tests);
    const mock_test_step = b.step("test-mock", "Run mock tests");
    mock_test_step.dependOn(&run_mock_tests.step);

    // Quick-cloning tests (mock HTTP, multipart body shape, validation)
    const quick_cloning_test_mod = b.createModule(.{
        .root_source_file = b.path("tests/quick_cloning_test.zig"),
        .target = target,
        .optimize = optimize,
        .imports = &.{.{ .name = "typecast", .module = typecast_mod }},
    });
    const quick_cloning_tests = b.addTest(.{ .root_module = quick_cloning_test_mod });
    const run_quick_cloning_tests = b.addRunArtifact(quick_cloning_tests);
    test_step.dependOn(&run_quick_cloning_tests.step);
    const quick_cloning_test_step = b.step("test-quick-cloning", "Run quick-cloning tests");
    quick_cloning_test_step.dependOn(&run_quick_cloning_tests.step);

    // Timestamps tests (fixture byte-equality + mock HTTP)
    const timestamps_test_mod = b.createModule(.{
        .root_source_file = b.path("tests/timestamps_test.zig"),
        .target = target,
        .optimize = optimize,
        .imports = &.{.{ .name = "typecast", .module = typecast_mod }},
    });
    const timestamps_tests = b.addTest(.{ .root_module = timestamps_test_mod });
    const run_timestamps_tests = b.addRunArtifact(timestamps_tests);
    const timestamps_test_step = b.step("test-timestamps", "Run timestamps tests");
    timestamps_test_step.dependOn(&run_timestamps_tests.step);

    // Integration tests (only when -Dintegration=true)
    const integration = b.option(bool, "integration", "Run integration tests") orelse false;
    if (integration) {
        const integration_test_mod = b.createModule(.{
            .root_source_file = b.path("tests/integration_test.zig"),
            .target = target,
            .optimize = optimize,
            .imports = &.{.{ .name = "typecast", .module = typecast_mod }},
        });
        const integration_tests = b.addTest(.{ .root_module = integration_test_mod });
        const run_integration_tests = b.addRunArtifact(integration_tests);
        const integration_test_step = b.step("test-integration", "Run integration tests");
        integration_test_step.dependOn(&run_integration_tests.step);
    }

    // Example
    const example_mod = b.createModule(.{
        .root_source_file = b.path("examples/tts.zig"),
        .target = target,
        .optimize = optimize,
        .imports = &.{.{ .name = "typecast", .module = typecast_mod }},
    });
    const tts_example = b.addExecutable(.{
        .name = "tts",
        .root_module = example_mod,
    });

    const run_example = b.addRunArtifact(tts_example);
    const example_step = b.step("example", "Run TTS example");
    example_step.dependOn(&run_example.step);

    b.installArtifact(tts_example);

    // WithTimestamps example
    const with_timestamps_mod = b.createModule(.{
        .root_source_file = b.path("examples/with_timestamps.zig"),
        .target = target,
        .optimize = optimize,
        .imports = &.{.{ .name = "typecast", .module = typecast_mod }},
    });
    const with_timestamps_example = b.addExecutable(.{
        .name = "with_timestamps",
        .root_module = with_timestamps_mod,
    });
    const run_with_timestamps = b.addRunArtifact(with_timestamps_example);
    const with_timestamps_step = b.step("example-with-timestamps", "Run TTS with timestamps example");
    with_timestamps_step.dependOn(&run_with_timestamps.step);
    b.installArtifact(with_timestamps_example);

    // Quick Voice Cloning example
    const quick_cloning_mod = b.createModule(.{
        .root_source_file = b.path("examples/quick_cloning.zig"),
        .target = target,
        .optimize = optimize,
        .imports = &.{.{ .name = "typecast", .module = typecast_mod }},
    });
    const quick_cloning_example = b.addExecutable(.{
        .name = "quick_cloning",
        .root_module = quick_cloning_mod,
    });
    const run_quick_cloning = b.addRunArtifact(quick_cloning_example);
    const quick_cloning_step = b.step("example-quick-cloning", "Run Quick Voice Cloning example");
    quick_cloning_step.dependOn(&run_quick_cloning.step);
    b.installArtifact(quick_cloning_example);
}
