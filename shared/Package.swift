// swift-tools-version:5.10
// Runs the Swift tests in swift-test/ against the Shared XCFramework that Gradle builds. Build the framework first:
//   ./gradlew :shared:assembleSharedDebugXCFramework
// then, from this directory:
//   xcodebuild test -scheme SharedSwiftTest -destination 'platform=iOS Simulator,name=iPhone 17'
// A local binary target must lie inside the package root, so this manifest sits in shared/, next to build/.
import PackageDescription

let package = Package(
    name: "SharedSwiftTest",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "SharedSwiftTest", targets: ["Shared"]),
    ],
    targets: [
        .binaryTarget(name: "Shared", path: "build/XCFrameworks/debug/Shared.xcframework"),
        .testTarget(
            name: "SharedSwiftTests",
            dependencies: ["Shared"],
            path: "swift-test/SharedSwiftTests",
            // A static Kotlin/Native framework needs the C++ runtime from the app that links it.
            linkerSettings: [.linkedLibrary("c++")]
        ),
    ]
)
