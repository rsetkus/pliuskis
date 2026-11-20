import XCTest

/// STORY-3 checks that need a person at the Simulator or a device build. Each test is skipped with the exact steps;
/// whoever runs them records the evidence (broker log lines, command output, screenshots) in
/// `_output/kmp_build_ios_app/stories/STORY-3.implementation.md`. The Local Network prompt never shows on the
/// Simulator; checking it on a real iPhone is tracked in Jira PLIUS-40.
final class Story3ManualChecksTests: XCTestCase {

    private static let prerequisites = """
        Prerequisites: staging broker running on the Mac (`docker compose up -d --build` in pliuskis-staging, \
        `docker compose ps` shows broker healthy, basil and fern running); \
        `cp iosApp/Configuration/Secrets.template.xcconfig iosApp/Configuration/Secrets.xcconfig` done once; \
        the Mac's LAN IP known (`ipconfig getifaddr en0`).
        """

    // AC23 (manual check)
    func testStory3_manualDebugOnSimulatorConnectsToLocalhost1883() throws {
        throw XCTSkip("""
            Manual check, AC23. \(Self.prerequisites)
            1. Build and run the Debug configuration on the iPhone 17 Simulator: \
            `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
            -destination 'platform=iOS Simulator,name=iPhone 17' build`, then install and launch it \
            (`xcrun simctl install booted <app path>`, `xcrun simctl launch booted lt.setkus.pliuskis`).
            2. Check the built app's host: `plutil -p <app path>/Info.plist | grep BrokerHost` shows "localhost".
            3. On the Mac run `docker compose logs -f broker`; expect `New client connected from 127.0.0.1 ... \
            on port 1883` with the app's 23-character client ID.
            4. Expect the device list to show Basil and Fern.
            Record the plutil line, the broker log line and a screenshot in the implementation map.
            """)
    }

    // AC23 (manual check)
    func testStory3_manualReleaseOnSimulatorConnectsToBrokerHostLanIp() throws {
        throw XCTSkip("""
            Manual check, AC23. \(Self.prerequisites)
            1. Set the Release host to the Mac's LAN IP (`BROKER_HOST` for Release in the xcconfig files, e.g. \
            192.168.1.20).
            2. Build the Release configuration for the Simulator: \
            `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
            -destination 'platform=iOS Simulator,name=iPhone 17' build`.
            3. Check the built app's host: `plutil -p <app path>/Info.plist | grep BrokerHost` shows the LAN IP.
            4. Install and launch it (`xcrun simctl install booted <app path>`, \
            `xcrun simctl launch booted lt.setkus.pliuskis`).
            5. On the Mac run `docker compose logs -f broker`; expect `New client connected from <LAN IP> ... \
            on port 1883` with the app's client ID, with no TLS.
            6. Expect the device list to show Basil and Fern.
            7. Terminate and launch the app again; expect the broker log to show the same client ID (per-install ID).
            Record the plutil line, both broker log lines and a screenshot in the implementation map.
            """)
    }

    // AC23 (manual check)
    func testStory3_manualUnsignedReleaseDeviceBuildSucceeds() throws {
        throw XCTSkip("""
            Manual check, AC23. \(Self.prerequisites)
            1. Run `xcodebuild build -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
            -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO`.
            2. Expect `** BUILD SUCCEEDED **` and exit code 0 (`echo $?`).
            Record the last lines of the output and the exit code in the implementation map.
            """)
    }
}
