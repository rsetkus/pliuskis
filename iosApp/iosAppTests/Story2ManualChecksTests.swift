import XCTest

/// STORY-2 checks run by hand or by script at the Simulator against the staging broker. Each test is skipped with the
/// exact steps; whoever runs them records the evidence (log lines, screenshots, times) in
/// `_output/kmp_build_ios_app/stories/STORY-2.implementation.md`.
/// The connection loss comes from the broker, so the Mac's network is not touched. Wi-Fi off/on and the Wi-Fi to
/// cellular switch need a real iPhone and are tracked in Jira PLIUS-40.
final class Story2ManualChecksTests: XCTestCase {

    private static let prerequisites = """
        Prerequisites: staging broker running on the Mac (`docker compose up -d --build` in pliuskis-staging, \
        `docker compose ps` shows broker healthy, basil and fern running); \
        `cp iosApp/Configuration/Secrets.template.xcconfig iosApp/Configuration/Secrets.xcconfig` done once.
        """

    // AC17 (manual check)
    func testStory2_manualReleaseBuildConnectsOverPlaintext1883() throws {
        throw XCTSkip("""
            Manual check, AC17. \(Self.prerequisites)
            1. Build and run the Release configuration on the Simulator: \
            `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
            -destination 'platform=iOS Simulator,name=iPhone 17' build`, then install and launch it \
            (`xcrun simctl install booted <app path>`, `xcrun simctl launch booted <bundle id>`).
            2. Run `docker logs -f pliuskis-staging-broker-1` and expect `New client connected from ...` \
            with the app's client ID, with no TLS.
            3. Expect the device list to show Basil and Fern.
            Record the broker log line and a screenshot in the implementation map.
            """)
    }

    // AC17 (manual check)
    func testStory2_manualBrokerGoneAndBackReconnectsAndSubscriptionsComeBack() throws {
        throw XCTSkip("""
            Manual check, AC17. \(Self.prerequisites)
            1. With the app running on the Simulator and the device list showing Basil and Fern, run \
            `docker pause pliuskis-staging-broker-1`, wait 30 s, then `docker unpause pliuskis-staging-broker-1`. \
            Repeat with `docker stop` / `docker start` of the broker.
            2. Each time, expect the broker log (`docker logs pliuskis-staging-broker-1`) to show the app's client \
            connecting again without a manual retry.
            3. Run `docker stop pliuskis-staging-basil-1`; expect Basil to show offline in the app without a manual \
            retry. Then `docker start pliuskis-staging-basil-1`; expect Basil online again.
            Record the broker log lines, the times and a screenshot in the implementation map.
            """)
    }

    // AC17 (manual check)
    func testStory2_manualTenMinutesInBackgroundThenForegroundReconnects() throws {
        throw XCTSkip("""
            Manual check, AC17. \(Self.prerequisites)
            1. With the app running on the Simulator and the device list showing, send it to the background \
            (`xcrun simctl launch booted com.apple.Preferences`) and leave it there for 10 minutes.
            2. During that time run `docker stop pliuskis-staging-fern-1`.
            3. Bring the app back to the foreground (`xcrun simctl launch booted <bundle id>`).
            4. Expect the broker log to show the app's client connected (reconnecting if the connection was dropped), \
            and Fern to show offline without a manual retry.
            5. Run `docker start pliuskis-staging-fern-1`; expect Fern online again.
            Record the broker log lines, times and a screenshot in the implementation map.
            """)
    }
}
