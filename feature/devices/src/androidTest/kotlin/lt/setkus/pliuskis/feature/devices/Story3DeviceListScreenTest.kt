package lt.setkus.pliuskis.feature.devices

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import lt.setkus.pliuskis.core.designsystem.theme.PliuskisTheme
import lt.setkus.pliuskis.feature.devices.DevicesListScreenState.Error
import lt.setkus.pliuskis.feature.devices.DevicesListScreenState.Loading
import lt.setkus.pliuskis.feature.devices.DevicesListScreenState.Success
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val EXCEPTION_MESSAGE = "socket closed"

/**
 * STORY-3: Device list screen shows the generic error screen on error.
 * Behaviour of AC13 (error texts from resources, no raw exception), AC14 (device cards),
 * AC15 (empty list) and AC16 (loading); in every non-error state the error screen is absent.
 */
@RunWith(AndroidJUnit4::class)
class Story3DeviceListScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(@StringRes id: Int): String = composeTestRule.activity.getString(id)

    private fun render(state: DevicesListScreenState) {
        composeTestRule.setContent {
            PliuskisTheme { DeviceListScreen(onDeviceClick = {}, devices = state, onRetry = {}) }
        }
    }

    private fun assertNoErrorScreen() {
        composeTestRule.onNodeWithText(string(R.string.devices_error_title)).assertDoesNotExist()
    }

    // AC13
    @Test
    fun testStory3_errorStateShowsErrorTextsNotExceptionMessage() {
        render(Error(Throwable(EXCEPTION_MESSAGE)))

        composeTestRule.onNodeWithText(string(R.string.devices_error_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.devices_error_message)).assertIsDisplayed()
        composeTestRule.onNodeWithText(EXCEPTION_MESSAGE, substring = true).assertDoesNotExist()
    }

    // AC14
    @Test
    fun testStory3_devicesStateShowsBothCardsAndNoErrorScreen() {
        render(
            Success(
                listOf(
                    DeviceListItem(name = "Alpha", id = "a", icon = R.drawable.ic_plant, online = true),
                    DeviceListItem(name = "Beta", id = "b", icon = R.drawable.ic_unknown, online = false)
                )
            )
        )

        composeTestRule.onNodeWithText("Alpha").assertIsDisplayed()
        composeTestRule.onNodeWithText("Beta").assertIsDisplayed()
        assertNoErrorScreen()
    }

    // AC15
    @Test
    fun testStory3_emptyStateShowsNoDevicesMessageAndNoErrorScreen() {
        render(Success(emptyList()))

        composeTestRule.onNodeWithText(string(R.string.devices_empty)).assertIsDisplayed()
        assertNoErrorScreen()
    }

    // AC16
    @Test
    fun testStory3_loadingStateShowsLoadingIndicatorAndNoErrorScreen() {
        // The loading wheel animates forever; pause the clock so the test doesn't wait for idle.
        composeTestRule.mainClock.autoAdvance = false
        render(Loading)
        composeTestRule.mainClock.advanceTimeByFrame()

        composeTestRule.onNodeWithContentDescription(string(R.string.devices_loading)).assertIsDisplayed()
        assertNoErrorScreen()
    }
}
