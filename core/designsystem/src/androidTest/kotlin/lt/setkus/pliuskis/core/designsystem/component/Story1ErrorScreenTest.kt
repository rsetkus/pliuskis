package lt.setkus.pliuskis.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import lt.setkus.pliuskis.core.designsystem.theme.PliuskisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TITLE = "T"
private const val MESSAGE = "M"
private const val ACTION = "A"

/**
 * STORY-1: Generic error screen component in the design system.
 * Behaviour of AC1 (title and message shown), AC2 (action click) and AC3 (no action, no button).
 */
@RunWith(AndroidJUnit4::class)
class Story1ErrorScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // AC1
    @Test
    fun testStory1_showsTitleAndMessage() {
        composeTestRule.setContent {
            PliuskisTheme { ErrorScreen(title = TITLE, message = MESSAGE) }
        }

        composeTestRule.onNodeWithText(TITLE).assertIsDisplayed()
        composeTestRule.onNodeWithText(MESSAGE).assertIsDisplayed()
    }

    // AC2
    @Test
    fun testStory1_actionClickInvokesCallbackOnce() {
        var clicks = 0
        composeTestRule.setContent {
            PliuskisTheme {
                ErrorScreen(title = TITLE, message = MESSAGE, actionLabel = ACTION, onAction = { clicks++ })
            }
        }

        composeTestRule.onNodeWithText(ACTION).performClick()

        assertEquals(1, clicks)
    }

    // AC3
    @Test
    fun testStory1_noActionShowsNoButton() {
        composeTestRule.setContent {
            PliuskisTheme { ErrorScreen(title = TITLE, message = MESSAGE) }
        }

        composeTestRule.onAllNodes(hasClickAction()).assertCountEquals(0)
    }
}
