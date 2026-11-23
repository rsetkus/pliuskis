package lt.setkus.pliuskis

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import lt.setkus.pliuskis.core.auth.AuthProvider
import lt.setkus.pliuskis.core.auth.AuthTokens
import lt.setkus.pliuskis.core.auth.Authenticator
import lt.setkus.pliuskis.core.auth.TokenStore
import lt.setkus.pliuskis.core.designsystem.theme.PliuskisTheme
import lt.setkus.pliuskis.data.util.NetworkConnectivity
import lt.setkus.pliuskis.feature.devices.navigation.DEVICES_ROUTE_PATTERN
import lt.setkus.pliuskis.ui.PliuskisApp
import lt.setkus.pliuskis.ui.rememberPliuskisAppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.koinInject
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import lt.setkus.pliuskis.feature.devices.R as DevicesR

private const val GOOGLE_BUTTON = "Continue with Google"
private const val ACCOUNT_MENU = "Account"
private const val SIGN_OUT = "Sign out"
private const val DELETE_ACCOUNT = "Delete account"
private const val TIMEOUT_MILLIS = 5_000L

/**
 * STORY-1 (kmp_cognito_login, PLIUS-34): the app's first screen follows the session, with a fake signed-in session.
 * Covers AC25 (sign-in or device list first, Back after sign-in closes the app, sign-out and "Delete account"
 * reachable from the device list) and the app side of AC21 (after sign-out the app shows sign-in and the device
 * screens' back stack entry, which owns the device list and control ViewModels, is destroyed).
 *
 * The real app (`PliuskisApp`, the app's Koin graph from `MyApplication`) runs with the auth and token-store ports
 * replaced through a Koin override, so sign-in and the launch refresh succeed at once without Cognito. The device list
 * still connects to the debug broker; with no broker it shows its error state, which is still the device list screen.
 */
@RunWith(AndroidJUnit4::class)
class Story1SignInNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val authenticator = AppFakeAuthenticator()
    private lateinit var tokenStore: AppInMemoryTokenStore
    private lateinit var navController: NavHostController

    private val devicesTitle: String
        get() = composeTestRule.activity.getString(DevicesR.string.devices_title)

    private fun launchApp(storedSession: AuthTokens?) {
        tokenStore = AppInMemoryTokenStore(storedSession)
        // Each test installs its own fake session over the app's bindings (Koin allows the override).
        loadKoinModules(
            module {
                single<Authenticator> { authenticator }
                single<TokenStore> { tokenStore }
            }
        )
        composeTestRule.setContent {
            val connectivity: NetworkConnectivity = koinInject()
            navController = rememberNavController()
            PliuskisTheme(darkTheme = false) {
                PliuskisApp(
                    connectivityMonitor = connectivity,
                    appState = rememberPliuskisAppState(connectivity, navHostController = navController),
                )
            }
        }
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(TIMEOUT_MILLIS) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForDeviceList() = waitForText(devicesTitle)

    private fun devicesEntry(): NavBackStackEntry {
        lateinit var entry: NavBackStackEntry
        composeTestRule.runOnUiThread { entry = navController.getBackStackEntry(DEVICES_ROUTE_PATTERN) }
        return entry
    }

    // AC25
    // GIVEN SignedOut (no stored session)
    // WHEN the app starts
    // THEN the first screen is sign-in, not the device list
    @Test
    fun testStory1_signedOutLaunchShowsSignInFirst() {
        launchApp(storedSession = null)

        waitForText(GOOGLE_BUTTON)

        composeTestRule.onNodeWithText(GOOGLE_BUTTON).assertIsDisplayed()
        composeTestRule.onNodeWithText(devicesTitle).assertDoesNotExist()
    }

    // AC25
    // GIVEN SignedOut and the sign-in screen
    // WHEN the user signs in with Google and the state becomes SignedIn
    // THEN the device list is shown, and system Back closes the app instead of returning to sign-in
    @Test
    fun testStory1_signingInShowsDeviceListAndBackClosesApp() {
        launchApp(storedSession = null)
        waitForText(GOOGLE_BUTTON)

        composeTestRule.onNodeWithText(GOOGLE_BUTTON).performClick()
        waitForDeviceList()

        assertEquals(listOf(AuthProvider.GOOGLE), authenticator.signInRequests)
        composeTestRule.onNodeWithText(GOOGLE_BUTTON).assertDoesNotExist()

        val activity = composeTestRule.activity
        Espresso.pressBackUnconditionally()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertTrue("Back after sign-in must close the app", activity.isFinishing || activity.isDestroyed)
    }

    // AC25
    // GIVEN SignedIn at launch (a stored session whose refresh succeeds)
    // WHEN the app starts
    // THEN the device list is the first screen and the sign-in screen is not shown
    @Test
    fun testStory1_signedInLaunchShowsDeviceListFirst() {
        launchApp(storedSession = APP_TEST_TOKENS)

        waitForDeviceList()

        composeTestRule.onNodeWithText(GOOGLE_BUTTON).assertDoesNotExist()
        assertEquals(emptyList<AuthProvider>(), authenticator.signInRequests)
    }

    // AC25
    // GIVEN SignedIn and the device list
    // WHEN the user opens the account menu
    // THEN "Sign out" and "Delete account" are shown
    @Test
    fun testStory1_signOutAndDeleteAccountAreReachableFromDeviceList() {
        launchApp(storedSession = APP_TEST_TOKENS)
        waitForDeviceList()

        composeTestRule.onNodeWithContentDescription(ACCOUNT_MENU).performClick()

        composeTestRule.onNodeWithText(SIGN_OUT).assertIsDisplayed()
        composeTestRule.onNodeWithText(DELETE_ACCOUNT).assertIsDisplayed()
    }

    // AC21 (app side)
    // GIVEN SignedIn and the device list
    // WHEN the user signs out from the account menu
    // THEN the refresh token is revoked once, the sign-in screen is shown, and the device screens' back stack entry
    //   (the owner of the device list and control ViewModels) is destroyed, which clears those ViewModels
    @Test
    fun testStory1_signOutShowsSignInAndClearsDeviceScreens() {
        launchApp(storedSession = APP_TEST_TOKENS)
        waitForDeviceList()
        val devices = devicesEntry()

        composeTestRule.onNodeWithContentDescription(ACCOUNT_MENU).performClick()
        composeTestRule.onNodeWithText(SIGN_OUT).performClick()
        waitForText(GOOGLE_BUTTON)

        composeTestRule.onNodeWithText(GOOGLE_BUTTON).assertIsDisplayed()
        composeTestRule.onNodeWithText(devicesTitle).assertDoesNotExist()
        assertEquals(listOf(APP_TEST_TOKENS.refreshToken), authenticator.revokedRefreshTokens)
        assertEquals(null, tokenStore.stored)
        composeTestRule.waitUntil(TIMEOUT_MILLIS) { devices.lifecycle.currentState == Lifecycle.State.DESTROYED }
    }
}
