package lt.setkus.pliuskis.feature.signin

import app.cash.paparazzi.Paparazzi
import lt.setkus.pliuskis.core.auth.AuthProvider
import lt.setkus.pliuskis.core.designsystem.component.PliuskisBackground
import lt.setkus.pliuskis.core.designsystem.theme.PliuskisTheme
import lt.setkus.pliuskis.core.presentation.signin.SignInError
import lt.setkus.pliuskis.core.presentation.signin.SignInState
import org.junit.Rule
import org.junit.Test

/**
 * STORY-1 (kmp_cognito_login, PLIUS-34), AC23: screenshots of the Android sign-in screen, on the app canvas as the app
 * shows it. Every snapshot shows the buttons "Continue with Google" and "Sign in with Apple"; the error states also
 * show an error line (no network, or any other failure).
 * Record with `./gradlew feature:signin:recordPaparazziDebug`, verify with `verifyPaparazziDebug`. The new goldens
 * are listed for review in the pull request.
 */
class Story1SignInScreenSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi()

    private fun snapshot(state: SignInState) {
        paparazzi.snapshot {
            PliuskisTheme(darkTheme = false) {
                PliuskisBackground { SignInScreen(state = state, onGoogleClick = {}, onAppleClick = {}) }
            }
        }
    }

    // AC23
    @Test
    fun testStory1_signedOutShowsGoogleAndAppleButtons() {
        snapshot(SignInState.SignedOut)
    }

    // AC23
    @Test
    fun testStory1_signingInWithGoogleShowsDisabledButtons() {
        snapshot(SignInState.SigningIn(AuthProvider.GOOGLE))
    }

    // AC23
    @Test
    fun testStory1_networkErrorShowsErrorLine() {
        snapshot(SignInState.Error(SignInError.NETWORK))
    }

    // AC23
    @Test
    fun testStory1_failedErrorShowsErrorLine() {
        snapshot(SignInState.Error(SignInError.FAILED))
    }
}
