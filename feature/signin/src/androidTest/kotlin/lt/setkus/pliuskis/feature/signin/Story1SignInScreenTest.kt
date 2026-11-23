package lt.setkus.pliuskis.feature.signin

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import lt.setkus.pliuskis.core.auth.AuthProvider
import lt.setkus.pliuskis.core.designsystem.theme.PliuskisTheme
import lt.setkus.pliuskis.core.presentation.signin.SignInState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The button texts AC23 and AC24 name. */
private const val GOOGLE_BUTTON = "Continue with Google"
private const val APPLE_BUTTON = "Sign in with Apple"

/**
 * STORY-1 (kmp_cognito_login, PLIUS-34), AC24: the Android sign-in screen calls the callback of the tapped provider
 * once, and disables both buttons while a sign-in runs.
 */
@RunWith(AndroidJUnit4::class)
class Story1SignInScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var googleClicks = 0
    private var appleClicks = 0

    private fun render(state: SignInState) {
        composeTestRule.setContent {
            PliuskisTheme(darkTheme = false) {
                SignInScreen(state = state, onGoogleClick = { googleClicks++ }, onAppleClick = { appleClicks++ })
            }
        }
    }

    // AC24
    // GIVEN SignedOut
    // WHEN the user taps "Continue with Google"
    // THEN the Google callback is called once and the Apple callback not at all
    @Test
    fun testStory1_tappingContinueWithGoogleCallsGoogleCallbackOnce() {
        render(SignInState.SignedOut)

        composeTestRule.onNodeWithText(GOOGLE_BUTTON).performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, googleClicks)
        assertEquals(0, appleClicks)
    }

    // AC24
    // GIVEN SignedOut
    // WHEN the user taps "Sign in with Apple"
    // THEN the Apple callback is called once and the Google callback not at all
    @Test
    fun testStory1_tappingSignInWithAppleCallsAppleCallbackOnce() {
        render(SignInState.SignedOut)

        composeTestRule.onNodeWithText(APPLE_BUTTON).performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, appleClicks)
        assertEquals(0, googleClicks)
    }

    // AC24
    // GIVEN SigningIn(GOOGLE)
    // WHEN the sign-in screen is shown
    // THEN both buttons are shown and disabled, and tapping them calls nothing
    @Test
    fun testStory1_signingInDisablesBothButtons() {
        // A progress indicator may animate while signing in; pause the clock so the test doesn't wait for idle.
        composeTestRule.mainClock.autoAdvance = false
        render(SignInState.SigningIn(AuthProvider.GOOGLE))
        composeTestRule.mainClock.advanceTimeByFrame()

        val google = composeTestRule.onNodeWithText(GOOGLE_BUTTON)
        val apple = composeTestRule.onNodeWithText(APPLE_BUTTON)
        google.assertIsDisplayed().assertIsNotEnabled()
        apple.assertIsDisplayed().assertIsNotEnabled()

        google.performClick()
        apple.performClick()
        composeTestRule.mainClock.advanceTimeByFrame()

        assertEquals(0, googleClicks)
        assertEquals(0, appleClicks)
    }
}
