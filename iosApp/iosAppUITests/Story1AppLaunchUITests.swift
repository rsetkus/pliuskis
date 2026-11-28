import XCTest

/// STORY-1: the app launches and its first screen shows a value that comes from the `Shared` (Kotlin) framework.
///
/// The app marks that text with the accessibility identifier `story1.kotlinValue`; the implementation map names the
/// Kotlin call that produces it.
///
/// Since sign-in (kmp_cognito_login STORY-1) a signed-out app opens on the sign-in screen, so the test starts the live
/// app with a fake signed-in session: the scenario `signedInLive` (Debug build only) shows the live device list with
/// its shared ViewModels, as after a sign-in.
final class Story1AppLaunchUITests: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
        #if !DEBUG
        throw XCTSkip("The fake signed-in session (-uiTestScenario signedInLive) exists only in the Debug build")
        #endif
    }

    /// AC4, on the Simulator.
    @MainActor
    func testStory1_firstScreenShowsValueFromKotlin() {
        let app = XCUIApplication()
        app.launchArguments += ["-uiTestScenario", "signedInLive"]
        app.launch()

        let kotlinValue = app.staticTexts["story1.kotlinValue"]

        XCTAssertTrue(kotlinValue.waitForExistence(timeout: 10), "No text with identifier story1.kotlinValue")
        XCTAssertFalse(kotlinValue.label.isEmpty)
    }
}
