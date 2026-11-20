import XCTest

/// STORY-4 checks that need a person at the Simulator and the staging broker. Each test is skipped with the exact
/// steps; whoever runs them records the evidence (screenshots, `docker compose` output, notes) in
/// `_output/kmp_build_ios_app/stories/STORY-4.implementation.md`.
final class Story4ManualChecksTests: XCTestCase {

    private static let prerequisites = """
        Prerequisites: in pliuskis-staging, `docker compose --profile faults up -d --build` (without the \
        profile only basil and fern run; sage and thyme need the `faults` profile, see its README.md); \
        `docker compose --profile faults ps` shows broker healthy and basil, fern, sage and thyme running (the broker may also hold a retained offline device \
        "Rasberry"); `cp iosApp/Configuration/Secrets.template.xcconfig iosApp/Configuration/Secrets.xcconfig` \
        done once (Debug host `localhost`).
        """

    // AC29 (manual check)
    func testStory4_manualDeviceListAgainstStagingShowsSortedDevicesAndOfflineDevice() throws {
        throw XCTSkip("""
            Manual check, AC29. \(Self.prerequisites)
            1. Build the Debug configuration for the Simulator: \
            `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
            -destination 'platform=iOS Simulator,name=iPhone 17' build`, then install and launch it without any \
            launch argument (`xcrun simctl install booted <app path>`, \
            `xcrun simctl launch booted lt.setkus.pliuskis`).
            2. Expect the device list (no -uiTestScenario, so the live DevicesViewModel) to show Basil, Fern, Sage and \
            Thyme in that order, each Online (a retained "Rasberry" may also show, Offline, in its sorted place \
            between Fern and Sage).
            3. Take a screenshot: `xcrun simctl io booted screenshot story4-list.png`.
            4. Stop one device container, e.g. `docker compose --profile faults stop sage`.
            5. Expect Sage's row to change to Offline within a few seconds, at the same place in the list, without \
            a manual retry; the other rows stay Online.
            6. Take a screenshot: `xcrun simctl io booted screenshot story4-sage-offline.png`.
            7. Start it again (`docker compose --profile faults start sage`) and expect Sage back Online.
            Record both screenshots, the docker commands with their output and notes in the implementation map.
            """)
    }
}
