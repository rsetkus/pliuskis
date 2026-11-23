import XCTest

/// STORY-1 (kmp_cognito_login, PLIUS-34), AC26: the SwiftUI sign-in screen in the states the shared `SignInViewModel`
/// produces, and the existing device and control scenarios still opening without sign-in.
///
/// The app runs a UI test scenario chosen with the launch argument `-uiTestScenario <name>` (see `UITestScenario`):
/// the real sign-in screen gets a fixed Kotlin `SignInState` and fake callbacks that count their calls. The scenarios
/// are compiled into the Debug build only (`#if DEBUG`), so these tests skip themselves in the Release build.
///
/// Scenarios and what they feed the screen:
/// - `signInSignedOut`: `SignInStateSignedOut`; the Google button counts its taps in `uiTestScenario.googleCount`,
///   Apple's standard "Sign in with Apple" button in `uiTestScenario.appleCount`.
/// - `signInSigningIn`: `SignInStateSigningIn(provider: GOOGLE)`.
/// - `signInError`: `SignInStateError(reason: FAILED)`.
///
/// Identifiers: `signIn.screen` (the screen), `signIn.google` (the Google button), `signIn.apple` (Apple's button),
/// `signIn.error` (the error line).
final class Story1SignInUITests: XCTestCase {

    private static let timeout: TimeInterval = 10

    override func setUpWithError() throws {
        continueAfterFailure = false
        #if !DEBUG
        throw XCTSkip("The -uiTestScenario launch argument exists only in the Debug build of iosApp")
        #endif
    }

    // AC26
    // GIVEN the sign-in state is SignedOut
    // WHEN the sign-in screen is shown
    // THEN it shows the Google button and Apple's "Sign in with Apple" button, both enabled, and no error line
    @MainActor
    func testStory1_signedOutScenarioShowsBothSignInButtons() {
        let app = launch(scenario: "signInSignedOut")

        XCTAssertTrue(element(app, "signIn.screen").waitForExistence(timeout: Self.timeout))
        let google = element(app, "signIn.google")
        let apple = element(app, "signIn.apple")
        XCTAssertTrue(google.exists)
        XCTAssertTrue(apple.exists)
        XCTAssertTrue(google.isEnabled)
        XCTAssertTrue(apple.isEnabled)
        XCTAssertFalse(element(app, "signIn.error").exists)
    }

    // AC26
    // GIVEN the sign-in state is SignedOut and no call was counted yet
    // WHEN the user taps the Google button once
    // THEN the Google callback counts one call and the Apple callback none
    @MainActor
    func testStory1_tappingGoogleButtonCountsOneGoogleCall() {
        let app = launch(scenario: "signInSignedOut")
        let google = element(app, "signIn.google")
        let googleCount = element(app, "uiTestScenario.googleCount")
        let appleCount = element(app, "uiTestScenario.appleCount")
        XCTAssertTrue(google.waitForExistence(timeout: Self.timeout))
        XCTAssertEqual(googleCount.label, "0")

        google.tap()

        XCTAssertTrue(waitFor(googleCount, label: "1"), "Google count was \(googleCount.label) after one tap")
        XCTAssertEqual(appleCount.label, "0")
    }

    // AC26
    // GIVEN the sign-in state is SignedOut and no call was counted yet
    // WHEN the user taps Apple's "Sign in with Apple" button once
    // THEN the Apple callback counts one call and the Google callback none
    @MainActor
    func testStory1_tappingAppleButtonCountsOneAppleCall() {
        let app = launch(scenario: "signInSignedOut")
        let apple = element(app, "signIn.apple")
        let googleCount = element(app, "uiTestScenario.googleCount")
        let appleCount = element(app, "uiTestScenario.appleCount")
        XCTAssertTrue(apple.waitForExistence(timeout: Self.timeout))
        XCTAssertEqual(appleCount.label, "0")

        apple.tap()

        XCTAssertTrue(waitFor(appleCount, label: "1"), "Apple count was \(appleCount.label) after one tap")
        XCTAssertEqual(googleCount.label, "0")
    }

    // AC26
    // GIVEN the sign-in state is SigningIn(GOOGLE)
    // WHEN the sign-in screen is shown
    // THEN both buttons are disabled
    @MainActor
    func testStory1_signingInScenarioDisablesBothButtons() {
        let app = launch(scenario: "signInSigningIn")

        XCTAssertTrue(element(app, "signIn.screen").waitForExistence(timeout: Self.timeout))
        let google = element(app, "signIn.google")
        let apple = element(app, "signIn.apple")
        XCTAssertTrue(google.exists)
        XCTAssertTrue(apple.exists)
        XCTAssertFalse(google.isEnabled, "The Google button must be disabled while signing in")
        XCTAssertFalse(apple.isEnabled, "The Apple button must be disabled while signing in")
    }

    // AC26
    // GIVEN the sign-in state is Error
    // WHEN the sign-in screen is shown
    // THEN the error line is shown, with text, next to the two buttons
    @MainActor
    func testStory1_errorScenarioShowsErrorLine() {
        let app = launch(scenario: "signInError")

        let error = element(app, "signIn.error")
        XCTAssertTrue(error.waitForExistence(timeout: Self.timeout))
        XCTAssertFalse(error.label.isEmpty, "The error line has no text")
        XCTAssertTrue(element(app, "signIn.google").exists)
        XCTAssertTrue(element(app, "signIn.apple").exists)
    }

    // AC26
    // GIVEN an existing device list scenario
    // WHEN the app starts with it
    // THEN the device list opens at once, without the sign-in screen
    @MainActor
    func testStory1_deviceScenarioOpensWithoutSignIn() {
        let app = launch(scenario: "devicesSuccess")

        XCTAssertTrue(element(app, "deviceList.row.b1").waitForExistence(timeout: Self.timeout))
        XCTAssertFalse(element(app, "signIn.screen").exists)
    }

    // AC26
    // GIVEN an existing control scenario
    // WHEN the app starts with it and the user taps the device
    // THEN its control screen opens, without the sign-in screen
    @MainActor
    func testStory1_controlScenarioOpensWithoutSignIn() {
        let app = launch(scenario: "controlStatus")
        let basil = element(app, "deviceList.row.b1")
        XCTAssertTrue(basil.waitForExistence(timeout: Self.timeout))
        XCTAssertFalse(element(app, "signIn.screen").exists)

        basil.tap()

        XCTAssertTrue(element(app, "control.screen").waitForExistence(timeout: Self.timeout))
        XCTAssertFalse(element(app, "signIn.screen").exists)
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

    @MainActor
    private func waitFor(_ element: XCUIElement, label: String) -> Bool {
        let predicate = NSPredicate(format: "label == %@", label)
        let expectation = XCTNSPredicateExpectation(predicate: predicate, object: element)
        return XCTWaiter.wait(for: [expectation], timeout: Self.timeout) == .completed
    }
}
