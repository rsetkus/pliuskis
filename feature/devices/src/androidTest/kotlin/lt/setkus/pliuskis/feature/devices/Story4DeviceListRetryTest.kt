package lt.setkus.pliuskis.feature.devices

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import lt.setkus.pliuskis.core.designsystem.theme.PliuskisTheme
import lt.setkus.pliuskis.feature.devices.DevicesListScreenState.Error
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * STORY-4: Retry from the device list error screen.
 * Behaviour of AC18 (tapping Retry calls the screen's retry callback once).
 */
@RunWith(AndroidJUnit4::class)
class Story4DeviceListRetryTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // AC18
    @Test
    fun testStory4_retryTapInvokesRetryCallbackOnce() {
        var retries = 0
        composeTestRule.setContent {
            PliuskisTheme {
                DeviceListScreen(
                    onDeviceClick = {},
                    devices = Error(Throwable("socket closed")),
                    onRetry = { retries++ }
                )
            }
        }

        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.devices_retry)).performClick()

        assertEquals(1, retries)
    }
}
