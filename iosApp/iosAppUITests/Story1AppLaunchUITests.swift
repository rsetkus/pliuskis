import XCTest

/// STORY-1: the app launches and its first screen shows a value that comes from the `Shared` (Kotlin) framework.
///
/// The app marks that text with the accessibility identifier `story1.kotlinValue`; the implementation map names the
/// Kotlin call that produces it.
final class Story1AppLaunchUITests: XCTestCase {

    override func setUp() {
        continueAfterFailure = false
    }

    /// AC4, on the Simulator.
    @MainActor
    func testStory1_firstScreenShowsValueFromKotlin() {
        let app = XCUIApplication()
        app.launch()

        let kotlinValue = app.staticTexts["story1.kotlinValue"]

        XCTAssertTrue(kotlinValue.waitForExistence(timeout: 10), "No text with identifier story1.kotlinValue")
        XCTAssertFalse(kotlinValue.label.isEmpty)
    }

    /// AC7: the app keeps running after launch. Run once more with `xcodebuild test -configuration Release` to cover
    /// the Release build; the implementation map records both runs.
    @MainActor
    func testStory1_appKeepsRunningAfterLaunch() {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10))
        XCTAssertTrue(app.staticTexts["story1.kotlinValue"].waitForExistence(timeout: 10))
        sleep(5)
        XCTAssertEqual(app.state, .runningForeground, "The app stopped within 5 s of launch")
    }
}
