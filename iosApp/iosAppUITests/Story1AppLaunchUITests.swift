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
}
