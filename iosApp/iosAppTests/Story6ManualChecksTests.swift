import XCTest

/// STORY-6 AC38: foreground return checks on the Simulator against the staging broker. Each test is skipped with the
/// exact steps; whoever runs them records the evidence (broker log lines, `simctl` commands, screenshots) in
/// `_output/kmp_build_ios_app/stories/STORY-6.implementation.md`. The Mac's network is never touched: connection loss
/// comes from stopping the broker, an offline device from stopping its staging container. The offline banner on a
/// real network loss is checked on a real iPhone in Jira PLIUS-40.
final class Story6ManualChecksTests: XCTestCase {

    private static let prerequisites = """
        Prerequisites: staging broker running on the Mac (`docker compose up -d --build` in pliuskis-staging, \
        `docker compose ps` shows broker healthy, basil and fern running); \
        `cp iosApp/Configuration/Secrets.template.xcconfig iosApp/Configuration/Secrets.xcconfig` done once; \
        the Debug app built and installed on the booted iPhone 17 Simulator \
        (`xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
        -destination 'platform=iOS Simulator,name=iPhone 17' build`, `xcrun simctl install booted <app path>`); \
        `docker compose logs -f broker` open in a second terminal; \
        the app's log open with `xcrun simctl spawn booted log stream --predicate 'process == "iosApp"'`.
        """

    // AC38 (manual check)
    func testStory6_manualBackgroundOneMinuteThenReturnUpdatesListAndStatus() throws {
        throw XCTSkip("""
            Manual check, AC38. \(Self.prerequisites)
            1. `xcrun simctl launch booted lt.setkus.pliuskis`; expect the device list with Basil and Fern online.
            2. Open Basil's control screen; expect humidity, water level and the last update time.
            3. Send the app to the background: `xcrun simctl launch booted com.apple.Preferences`.
            4. Wait 1 minute.
            5. Bring the app back: `xcrun simctl launch booted lt.setkus.pliuskis`.
            6. Expect, without tapping retry: the control screen's last update time moves to now (a new status \
            request in the broker log: `Received PUBLISH ... 'devices/<basil id>/status'`), and back on the list \
            Basil and Fern still show online. Expect at most one new CONNECT from the app's client ID in the broker \
            log.
            Record the broker log lines and before/after screenshots \
            (`xcrun simctl io booted screenshot story6-bg1m-<n>.png`) in the implementation map.
            """)
    }

    // AC38 (manual check)
    func testStory6_manualBackgroundThirtyMinutesThenReturnUpdatesListAndStatus() throws {
        throw XCTSkip("""
            Manual check, AC38. \(Self.prerequisites)
            1. `xcrun simctl launch booted lt.setkus.pliuskis`; open Basil's control screen.
            2. Send the app to the background: `xcrun simctl launch booted com.apple.Preferences`.
            3. Wait 30 minutes (the socket is expected to be gone: the broker log shows the app's client ID \
            disconnecting or timing out).
            4. Bring the app back: `xcrun simctl launch booted lt.setkus.pliuskis`.
            5. Expect, without tapping retry: one new CONNECT from the app's client ID in the broker log, then \
            SUBSCRIBE and a status request for Basil; the control screen's last update time moves to now; back on \
            the list Basil and Fern show online.
            Record the broker log lines and screenshots in the implementation map.
            """)
    }

    // AC38 (manual check)
    func testStory6_manualLockAndUnlockUpdatesListAndStatus() throws {
        throw XCTSkip("""
            Manual check, AC38. \(Self.prerequisites)
            1. `xcrun simctl launch booted lt.setkus.pliuskis`; open Basil's control screen.
            2. Lock the Simulator: Device > Lock in the Simulator menu (Cmd+L). Wait 1 minute.
            3. Unlock: press Home (Device > Home, Shift+Cmd+H) and swipe up on the lock screen.
            4. Expect the same result as the background check, without tapping retry: the control screen's last \
            update time moves to now (a new status request in the broker log), and the list shows Basil and Fern \
            online.
            Record the broker log lines and screenshots in the implementation map.
            """)
    }

    // AC38 (manual check)
    func testStory6_manualBrokerStopAndStartWithAppOpenRecoversData() throws {
        throw XCTSkip("""
            Manual check, AC38. \(Self.prerequisites)
            1. `xcrun simctl launch booted lt.setkus.pliuskis`; the device list shows Basil and Fern online.
            2. With the app open, stop the broker: `docker compose stop broker`. Wait 30 seconds.
            3. Start it again: `docker compose start broker`; the devices reconnect and publish their retained \
            info and availability again.
            4. Expect, without tapping retry: the app reconnects (a CONNECT from its client ID in the broker log, \
            then SUBSCRIBE to `devices/+/info` and `devices/+/availability`), and the list shows Basil and Fern \
            online again.
            5. Open Basil's control screen and repeat steps 2 and 3 with the screen open. Expect, without tapping \
            retry and without leaving the app: after the app's new CONNECT, exactly one new status request for Basil \
            in the broker log (`Received PUBLISH ... 'devices/<basil id>/status'`), no PUBLISH on \
            `devices/<basil id>/command`, and the control screen's last update time moves to now on its own.
            Record the broker log lines and screenshots in the implementation map.
            """)
    }

    // AC38 (manual check)
    func testStory6_manualDeviceStoppedWhileBackgroundedShowsOfflineOnReturn() throws {
        throw XCTSkip("""
            Manual check, AC38. \(Self.prerequisites)
            1. `xcrun simctl launch booted lt.setkus.pliuskis`; the device list shows Basil and Fern online.
            2. Send the app to the background: `xcrun simctl launch booted com.apple.Preferences`.
            3. Stop one staging device: `docker compose stop basil` (the broker sets its retained availability to \
            `offline`). Wait 1 minute.
            4. Bring the app back: `xcrun simctl launch booted lt.setkus.pliuskis`.
            5. Expect, without tapping retry: Basil shows offline and Fern online.
            6. `docker compose start basil`; expect Basil to show online again with the app open.
            Record the broker log lines and screenshots in the implementation map.
            """)
    }
}
