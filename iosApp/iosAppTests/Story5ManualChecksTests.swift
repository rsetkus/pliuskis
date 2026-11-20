import XCTest

/// STORY-5 checks that need a person at the Simulator and the staging broker. Each test is skipped with the exact
/// steps; whoever runs them records the evidence (screenshots, `mosquitto_sub` output, `docker compose` commands,
/// notes) in `_output/kmp_build_ios_app/stories/STORY-5.implementation.md`. The Mac's network is never touched.
final class Story5ManualChecksTests: XCTestCase {

    private static let prerequisites = """
        Prerequisites: in pliuskis-staging, `docker compose --profile faults up -d --build` (without the profile only \
        basil and fern run; sage and thyme need the `faults` profile); `docker compose --profile faults ps` shows \
        broker healthy and basil, fern, sage and thyme running; \
        `cp iosApp/Configuration/Secrets.template.xcconfig iosApp/Configuration/Secrets.xcconfig` done once (Debug \
        host `localhost`); the Debug app built and installed on the booted iPhone 17 Simulator \
        (`xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
        -destination 'platform=iOS Simulator,name=iPhone 17' build`, `xcrun simctl install booted <app path>`), \
        launched without any launch argument (`xcrun simctl launch booted lt.setkus.pliuskis`), so the live \
        ViewModels run; mosquitto-clients installed on the Mac.
        """

    // AC35 (manual check)
    func testStory5_manualControlScreenAgainstStagingShowsStatusErrorAndWatersOnce() throws {
        throw XCTSkip("""
            Manual check, AC35. \(Self.prerequisites)
            1. In a second terminal: `mosquitto_sub -h localhost -v -t 'devices/+/command' -t 'devices/+/status' \
            -t 'application/#'` and keep it open.
            2. Tap Basil. Expect the healthy status: the humidity text for GOOD and the water level text for ENOUGH \
            (staging basil: humidity 8, water level 9), and a last update time of now in the Simulator's locale and \
            time zone. Screenshot: `xcrun simctl io booted screenshot story5-basil.png`.
            3. Back, tap Fern. Expect the THIRSTY humidity text and the LOW water level text (staging fern: humidity \
            3, water level 2). Screenshot: `story5-fern.png`.
            4. Back, tap Sage. Staging sage (`FAULT: silent`) never answers and ControlViewModel has no status \
            timeout, so expect the screen to stay on Loading after 30 s, as on Android. Screenshot: `story5-sage.png`.
            5. Back, tap Thyme (undecodable status). Expect the error view. Screenshot: `story5-thyme.png`.
            6. Back, tap Basil, wait for its status, then tap the water button once.
            7. In the mosquitto_sub terminal expect exactly one line `devices/basil/command {"command":"water"}` for \
            this tap (and no second one within 30 s), and basil's next status reply on \
            `application/<this install's client ID>/basil/status`; the screen updates to the new reading.
            Record the screenshots, the mosquitto_sub output and notes in the implementation map.
            """)
    }
}
