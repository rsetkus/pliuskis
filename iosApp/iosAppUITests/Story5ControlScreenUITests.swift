import XCTest

/// STORY-5: the SwiftUI control screen shows the state the shared `ControlViewModel` produces (humidity, water level,
/// last update time, the water button, the error view) and Back returns to the device list, clearing the screen's
/// ViewModel once.
///
/// The app runs a UI test scenario chosen with `-uiTestScenario <name>` (Debug build only, `#if DEBUG`, so these tests
/// skip themselves in Release). Each control scenario shows the device list `Success([basil (b1, online)])` inside the
/// app's real `DevicesNavigation`; tapping basil pushes the control screen, which gets a fixed Kotlin
/// `ControlUiState` instead of the live ViewModel, and a fake water action. Test controls sit outside the navigation
/// stack, so they stay on screen after Back:
/// - `uiTestScenario.waterCount`: how many times the screen called its water action (the ViewModel's `waterPlant()`
///   in the live app);
/// - `uiTestScenario.controlClearedCount`: how many times the control screen's ViewModel was cleared. The scenario's
///   control screen owns a Swift `Lifecycle_viewmodelViewModel` subclass that counts `onCleared`, through
///   `IosViewModelStoreOwner` held with `@StateObject`, the same way the live control screen owns `ControlViewModel`;
/// - `uiTestScenario.nextControlState` (only in `controlStateSequence`): moves the control state one step.
///
/// Scenarios:
/// - `controlStatus`: `Success(DeviceStatus(humidity: .thirsty, waterLevel: .low, updatedAt: 2026-11-19T10:15:00Z))`.
///   The scenario fixes the control screen's SwiftUI environment to `\.locale` en_GB and `\.timeZone` Europe/Vilnius
///   (UTC+2 that day), so the shown time is 12:15.
/// - `controlLoading`: `Loading`.
/// - `controlError`: `Error(message: "UI test scenario error")`.
/// - `controlStateSequence`: starts `Loading`; `uiTestScenario.nextControlState` moves to the `controlStatus` Success,
///   then to `Error`.
///
/// Watering states (AC32): the Android control screen (`feature/control` `ControlScreen`) has no separate watering
/// state. `waterPlant()` changes no UI state; the "Add water" button exists only in `Success`, enabled, and stays
/// enabled and unchanged after a tap. `Loading` shows the loading indicator and `Error` the error view, both without
/// the button. The iOS screen shows the same states in the same order: Loading (no button) -> Success (enabled
/// button) -> Error (no button).
///
/// Accessibility identifiers the screen provides:
/// `control.screen` (whole screen, every state), `control.deviceId` (label = deviceId, every state; STORY-4's AC27
/// test uses both), `control.loading`, `control.humidity`, `control.waterLevel`, `control.updatedAt`, `control.water`
/// (the button), `control.error`.
final class Story5ControlScreenUITests: XCTestCase {

    private static let timeout: TimeInterval = 10

    override func setUpWithError() throws {
        continueAfterFailure = false
        #if !DEBUG
        throw XCTSkip("The -uiTestScenario launch argument exists only in the Debug build of iosApp")
        #endif
    }

    // AC30
    // GIVEN a control state Success with HumidityStatus THIRSTY, WaterLevelStatus LOW and lastUpdate
    //   2026-11-19T10:15:00Z, locale en_GB and time zone Europe/Vilnius
    // WHEN the control screen of b1 is shown
    // THEN it shows the THIRSTY and LOW display texts and the update time formatted by Swift in that zone (12:15)
    @MainActor
    func testStory5_statusStateShowsHumidityWaterLevelAndUpdateTime() {
        let app = openControlScreen(scenario: "controlStatus")

        let humidity = element(app, "control.humidity")
        let waterLevel = element(app, "control.waterLevel")
        let updatedAt = element(app, "control.updatedAt")
        XCTAssertTrue(humidity.waitForExistence(timeout: Self.timeout))
        XCTAssertTrue(waterLevel.exists)
        XCTAssertTrue(updatedAt.exists)

        XCTAssertTrue(humidity.label.localizedCaseInsensitiveContains("thirsty"), "Humidity was \"\(humidity.label)\"")
        XCTAssertTrue(waterLevel.label.localizedCaseInsensitiveContains("low"), "Water level was \"\(waterLevel.label)\"")
        XCTAssertTrue(updatedAt.label.contains("12:15"), "Update time was \"\(updatedAt.label)\"")
        XCTAssertFalse(updatedAt.label.contains("10:15"), "Update time is in UTC: \"\(updatedAt.label)\"")
        XCTAssertTrue(updatedAt.label.contains("19"), "Update time has no day: \"\(updatedAt.label)\"")
        XCTAssertTrue(updatedAt.label.contains("2026"), "Update time has no year: \"\(updatedAt.label)\"")
        XCTAssertEqual(element(app, "control.deviceId").label, "b1")
        XCTAssertFalse(element(app, "control.loading").exists)
        XCTAssertFalse(element(app, "control.error").exists)
    }

    // AC32
    // GIVEN the control screen in the status state and no water tap counted yet
    // WHEN the user taps the water button once
    // THEN the water action was called exactly once, and the button stays shown, enabled and unchanged (like Android)
    @MainActor
    func testStory5_tappingWaterCallsWaterActionOnceAndButtonStaysEnabled() {
        let app = openControlScreen(scenario: "controlStatus")
        let water = app.buttons["control.water"]
        let waterCount = element(app, "uiTestScenario.waterCount")
        XCTAssertTrue(water.waitForExistence(timeout: Self.timeout))
        XCTAssertTrue(water.isEnabled)
        XCTAssertFalse(water.label.isEmpty)
        let labelBeforeTap = water.label
        XCTAssertEqual(waterCount.label, "0")

        water.tap()

        XCTAssertTrue(waitFor(waterCount, label: "1"), "Water count was \(waterCount.label) after one tap")
        // No second call arrives after the tap.
        RunLoop.current.run(until: Date().addingTimeInterval(1))
        XCTAssertEqual(waterCount.label, "1")
        XCTAssertTrue(water.exists)
        XCTAssertTrue(water.isEnabled)
        XCTAssertEqual(water.label, labelBeforeTap)
    }

    // AC32
    // GIVEN the control state Loading
    // WHEN the control screen is shown
    // THEN it shows the loading indicator and no water button (Android shows only its loading wheel)
    @MainActor
    func testStory5_loadingStateShowsLoadingIndicatorAndNoWaterButton() {
        let app = openControlScreen(scenario: "controlLoading")

        XCTAssertTrue(element(app, "control.loading").waitForExistence(timeout: Self.timeout))
        XCTAssertFalse(app.buttons["control.water"].exists)
        XCTAssertEqual(element(app, "control.deviceId").label, "b1")
    }

    // AC32
    // GIVEN the ViewModel states Loading, then Success, then Error, in that order
    // WHEN the control screen shows each in turn
    // THEN the water button goes: absent -> shown and enabled -> absent, as on Android for the same states
    @MainActor
    func testStory5_waterButtonFollowsViewModelStatesInAndroidOrder() {
        let app = openControlScreen(scenario: "controlStateSequence")
        let water = app.buttons["control.water"]
        let next = app.buttons["uiTestScenario.nextControlState"]

        XCTAssertTrue(element(app, "control.loading").waitForExistence(timeout: Self.timeout))
        XCTAssertFalse(water.exists, "Loading must not show the water button")

        next.tap()
        XCTAssertTrue(water.waitForExistence(timeout: Self.timeout), "Success must show the water button")
        XCTAssertTrue(water.isEnabled)
        XCTAssertFalse(element(app, "control.loading").exists)

        next.tap()
        XCTAssertTrue(element(app, "control.error").waitForExistence(timeout: Self.timeout))
        XCTAssertTrue(waitForNonExistence(water), "Error must not show the water button")
    }

    // AC33
    // GIVEN the control state Error
    // WHEN the control screen is shown
    // THEN it shows the error view, without status values or the water button
    @MainActor
    func testStory5_errorStateShowsErrorView() {
        let app = openControlScreen(scenario: "controlError")

        let error = element(app, "control.error")
        XCTAssertTrue(error.waitForExistence(timeout: Self.timeout))
        XCTAssertFalse(error.label.isEmpty)
        XCTAssertFalse(app.buttons["control.water"].exists)
        XCTAssertFalse(element(app, "control.humidity").exists)
        XCTAssertFalse(element(app, "control.loading").exists)
    }

    // AC33
    // GIVEN the control screen of b1 in the Error state, its ViewModel not cleared yet
    // WHEN the user taps Back
    // THEN the device list is shown again and the control screen's ViewModel was cleared exactly once
    @MainActor
    func testStory5_backFromErrorReturnsToDeviceListAndClearsViewModelOnce() {
        let app = openControlScreen(scenario: "controlError")
        let clearedCount = element(app, "uiTestScenario.controlClearedCount")
        XCTAssertTrue(element(app, "control.error").waitForExistence(timeout: Self.timeout))
        XCTAssertEqual(clearedCount.label, "0", "The ViewModel was cleared while its screen was shown")

        app.navigationBars.buttons.element(boundBy: 0).tap()

        XCTAssertTrue(element(app, "deviceList.row.b1").waitForExistence(timeout: Self.timeout))
        XCTAssertTrue(waitForNonExistence(element(app, "control.screen")), "The control screen is still shown")
        XCTAssertTrue(waitFor(clearedCount, label: "1"), "Cleared count was \(clearedCount.label) after Back")
        // onCleared runs once, not again later.
        RunLoop.current.run(until: Date().addingTimeInterval(1))
        XCTAssertEqual(clearedCount.label, "1")
    }

    // MARK: - Helpers

    /// Launches `scenario` and opens basil's (b1) control screen from the device list, as a user does.
    @MainActor
    private func openControlScreen(scenario: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += ["-uiTestScenario", scenario]
        app.launch()
        let basil = element(app, "deviceList.row.b1")
        XCTAssertTrue(basil.waitForExistence(timeout: Self.timeout))
        basil.tap()
        XCTAssertTrue(element(app, "control.screen").waitForExistence(timeout: Self.timeout))
        return app
    }

    @MainActor
    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }

    @MainActor
    private func waitFor(_ element: XCUIElement, label: String) -> Bool {
        let predicate = NSPredicate(format: "label == %@", label)
        let expectation = XCTNSPredicateExpectation(predicate: predicate, object: element)
        return XCTWaiter.wait(for: [expectation], timeout: Self.timeout) == .completed
    }

    @MainActor
    private func waitForNonExistence(_ element: XCUIElement) -> Bool {
        let expectation = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: element)
        return XCTWaiter.wait(for: [expectation], timeout: Self.timeout) == .completed
    }
}
