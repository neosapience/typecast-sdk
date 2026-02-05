// swift-tools-version: 5.9
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "Typecast",
    platforms: [
        .iOS(.v13),
        .macOS(.v10_15),
        .tvOS(.v13),
        .watchOS(.v6),
        .visionOS(.v1)
    ],
    products: [
        .library(
            name: "Typecast",
            targets: ["Typecast"]
        ),
    ],
    targets: [
        .target(
            name: "Typecast",
            dependencies: [],
            path: "Sources/Typecast"
        ),
        .testTarget(
            name: "TypecastTests",
            dependencies: ["Typecast"],
            path: "Tests/TypecastTests"
        ),
    ]
)
