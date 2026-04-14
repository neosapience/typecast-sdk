// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "SwiftStreamingTest",
    platforms: [.macOS(.v12)],
    dependencies: [
        .package(path: "../../typecast-swift"),
    ],
    targets: [
        .executableTarget(
            name: "SwiftStreamingTest",
            dependencies: [
                .product(name: "Typecast", package: "typecast-swift"),
            ],
            path: "Sources"
        ),
    ]
)
