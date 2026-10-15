package lt.setkus.pliuskis.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import lt.setkus.pliuskis.core.designsystem.theme.PliuskisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TITLE = "T"
private const val MESSAGE = "M"
private const val ACTION = "A"
private const val LARGE_FONT_SCALE = 1.5f
private const val LONG_MESSAGE = "We couldn't reach your devices. Check your connection, then try again in a moment."

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

    // AC2, in a short window with large text (landscape, split screen)
    @Test
    fun testStory1_actionReachableInShortWindowWithLargeText() {
        var clicks = 0
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT_SCALE)) {
                PliuskisTheme {
                    Box(modifier = Modifier.size(width = 320.dp, height = 180.dp)) {
                        ErrorScreen(
                            title = TITLE,
                            message = LONG_MESSAGE,
                            actionLabel = ACTION,
                            onAction = { clicks++ }
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithText(ACTION).performScrollTo().assertIsDisplayed().performClick()

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
