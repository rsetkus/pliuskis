import XCTest

/// STORY-4: the SwiftUI device list screen shows the states the shared `DevicesViewModel` produces, the offline banner
/// from the shared connectivity state, and opens the control screen for a tapped device.
///
/// The app runs a UI test scenario chosen with the launch argument `-uiTestScenario <name>`: instead of the live
/// ViewModels, the device list screen (inside the app's real `DevicesNavigation`) gets fixed Kotlin state objects
/// (`DevicesListScreenState`, `DeviceListItem`) and fake callbacks. The scenarios are compiled into the Debug build
/// only (`#if DEBUG`), so these tests skip themselves in the Release build of this bundle.
///
/// Scenarios and what they feed the screen:
/// - `devicesLoading`: `Loading`, online.
/// - `devicesSuccess`: `Success([basil (b1, online), fern (f1, offline)])`, already sorted by Kotlin, online.
/// - `devicesEmpty`: `Success([])`, online; "Search again" increments `uiTestScenario.retryCount`.
/// - `devicesError`: `Error`, online; Retry increments a counter shown as `uiTestScenario.retryCount` and keeps the
///   state `Error`.
/// - `devicesOffline`: `Success([basil (b1, online)])`, offline; the button `uiTestScenario.goOnline` (outside the
///   screen) switches the fake connectivity to online.
/// - `devicesUnsorted`: `Success([thyme (t1), basil (b1), sage (s1)])`, in that order, all online.
final class Story4DeviceListUITests: XCTestCase {

    private static let timeout: TimeInterval = 10

    override func setUpWithError() throws {
        continueAfterFailure = false
        #if !DEBUG
        throw XCTSkip("The -uiTestScenario launch argument exists only in the Debug build of iosApp")
        #endif
    }

    // AC24
    // GIVEN the DevicesViewModel state is Loading
    // WHEN the device list screen is shown
    // THEN it shows a progress indicator and no rows
    @MainActor
    func testStory4_loadingStateShowsProgressIndicator() {
        let app = launch(scenario: "devicesLoading")

        XCTAssertTrue(element(app, "deviceList.loading").waitForExistence(timeout: Self.timeout))
        XCTAssertEqual(rows(app).count, 0)
    }

    // AC24
    // GIVEN the state is Success with ["basil" online, "fern" offline], sorted by Kotlin
    // WHEN the device list screen is shown
    // THEN it shows the row "basil" above the row "fern", each with its online/offline state
    @MainActor
    func testStory4_successStateShowsRowsInOrderWithOnlineState() {
        let app = launch(scenario: "devicesSuccess")

        let basil = element(app, "deviceList.row.b1")
        let fern = element(app, "deviceList.row.f1")
        XCTAssertTrue(basil.waitForExistence(timeout: Self.timeout))
        XCTAssertTrue(fern.exists)

        XCTAssertEqual(basil.label, "basil")
        XCTAssertEqual(basil.value as? String, "Online")
        XCTAssertEqual(fern.label, "fern")
        XCTAssertEqual(fern.value as? String, "Offline")
        XCTAssertEqual(rowLabelsTopToBottom(app), ["basil", "fern"])
        XCTAssertFalse(element(app, "deviceList.loading").exists)
    }

    // AC24
    // GIVEN the state is Success with an empty list
    // WHEN the device list screen is shown
    // THEN it shows the empty-state text, a "Search again" action, and no rows
    @MainActor
    func testStory4_emptySuccessStateShowsEmptyStateText() {
        let app = launch(scenario: "devicesEmpty")

        let empty = element(app, "deviceList.empty")
        XCTAssertTrue(empty.waitForExistence(timeout: Self.timeout))
        XCTAssertTrue(
            element(app, "deviceList.empty").staticTexts["No devices yet"].exists,
            "Empty-state text was \"\(empty.label)\""
        )
        XCTAssertTrue(app.buttons["deviceList.searchAgain"].exists)
        XCTAssertEqual(rows(app).count, 0)
    }

    // GIVEN the state is Success with an empty list
    // WHEN the user taps "Search again" once
    // THEN the screen calls its retry action exactly once (the ViewModel's retry() runs discovery again)
    @MainActor
    func testStory4_searchAgainOnEmptyListCallsRetryOnce() {
        let app = launch(scenario: "devicesEmpty")
        let searchAgain = app.buttons["deviceList.searchAgain"]
        let retryCount = element(app, "uiTestScenario.retryCount")
        XCTAssertTrue(searchAgain.waitForExistence(timeout: Self.timeout))
        XCTAssertEqual(retryCount.label, "0")

        searchAgain.tap()

        XCTAssertTrue(waitFor(retryCount, label: "1"), "Retry count was \(retryCount.label) after one tap")
    }

    // AC25
    // GIVEN the state is Error
    // WHEN the device list screen is shown
    // THEN it shows the error view with a Retry action, and no rows
    @MainActor
    func testStory4_errorStateShowsErrorViewWithRetry() {
        let app = launch(scenario: "devicesError")

        XCTAssertTrue(element(app, "deviceList.error").waitForExistence(timeout: Self.timeout))
        let retry = app.buttons["deviceList.retry"]
        XCTAssertTrue(retry.exists)
        XCTAssertEqual(retry.label, "Retry")
        XCTAssertEqual(rows(app).count, 0)
    }

    // AC25
    // GIVEN the error view is shown and the fake has counted no retry yet
    // WHEN the user taps Retry once
    // THEN retry() was called exactly once
    @MainActor
    func testStory4_tappingRetryCallsRetryExactlyOnce() {
        let app = launch(scenario: "devicesError")
        let retry = app.buttons["deviceList.retry"]
        let retryCount = element(app, "uiTestScenario.retryCount")
        XCTAssertTrue(retry.waitForExistence(timeout: Self.timeout))
        XCTAssertEqual(retryCount.label, "0")

        retry.tap()

        XCTAssertTrue(waitFor(retryCount, label: "1"), "Retry count was \(retryCount.label) after one tap")
        // No second call arrives after the tap.
        RunLoop.current.run(until: Date().addingTimeInterval(1))
        XCTAssertEqual(retryCount.label, "1")
    }

    // AC26
    // GIVEN the shared connectivity state is offline
    // WHEN the device list screen is shown
    // THEN the offline banner is shown; when the connectivity becomes online, the banner disappears
    @MainActor
    func testStory4_offlineBannerShowsWhileOfflineAndDisappearsWhenOnline() {
        let app = launch(scenario: "devicesOffline")
        let banner = element(app, "deviceList.offlineBanner")
        XCTAssertTrue(banner.waitForExistence(timeout: Self.timeout))
        XCTAssertFalse(banner.label.isEmpty)
        XCTAssertTrue(element(app, "deviceList.row.b1").exists, "The banner must not replace the list")

        app.buttons["uiTestScenario.goOnline"].tap()

        XCTAssertTrue(waitForNonExistence(banner), "The offline banner is still shown after going online")
        XCTAssertTrue(element(app, "deviceList.row.b1").exists)
    }

    // AC26
    // GIVEN the shared connectivity state is online
    // WHEN the device list screen is shown
    // THEN there is no offline banner
    @MainActor
    func testStory4_noOfflineBannerWhileOnline() {
        let app = launch(scenario: "devicesSuccess")

        XCTAssertTrue(element(app, "deviceList.row.b1").waitForExistence(timeout: Self.timeout))
        XCTAssertFalse(element(app, "deviceList.offlineBanner").exists)
    }

    // AC27
    // GIVEN a Success list with device "basil" (deviceId "b1")
    // WHEN the user taps its row
    // THEN the control screen opens for that device, titled with its name
    @MainActor
    func testStory4_tappingDeviceRowOpensControlScreenForItsDeviceId() {
        let app = launch(scenario: "devicesSuccess")
        let basil = element(app, "deviceList.row.b1")
        XCTAssertTrue(basil.waitForExistence(timeout: Self.timeout))

        basil.tap()

        XCTAssertTrue(element(app, "control.screen").waitForExistence(timeout: Self.timeout))
        let title = element(app, "control.title")
        XCTAssertTrue(title.exists)
        XCTAssertEqual(title.label, "basil")
    }

    // AC28 (behaviour)
    // GIVEN Kotlin emits the unsorted list [thyme, basil, sage]
    // WHEN the device list screen is shown
    // THEN the rows are in the received order: thyme, basil, sage
    @MainActor
    func testStory4_unsortedListFromKotlinIsShownInReceivedOrder() {
        let app = launch(scenario: "devicesUnsorted")

        XCTAssertTrue(element(app, "deviceList.row.s1").waitForExistence(timeout: Self.timeout))
        XCTAssertEqual(rowLabelsTopToBottom(app), ["thyme", "basil", "sage"])
    }

    // MARK: - Helpers

    @MainActor
    private func launch(scenario: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += ["-uiTestScenario", scenario]
        app.launch()
        return app
    }

    @MainActor
    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }

    /// Every device row on screen; a row's identifier is `deviceList.row.<deviceId>`.
    @MainActor
    private func rows(_ app: XCUIApplication) -> [XCUIElement] {
        let query = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH %@", "deviceList.row."))
        // A row may be exposed as a cell and as its button; keep one element per identifier.
        var seen = Set<String>()
        return query.allElementsBoundByIndex.filter { seen.insert($0.identifier).inserted }
    }

    /// The labels (device names) of the rows, from the top of the screen down.
    @MainActor
    private func rowLabelsTopToBottom(_ app: XCUIApplication) -> [String] {
        rows(app)
            .sorted { $0.frame.minY < $1.frame.minY }
            .map(\.label)
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
