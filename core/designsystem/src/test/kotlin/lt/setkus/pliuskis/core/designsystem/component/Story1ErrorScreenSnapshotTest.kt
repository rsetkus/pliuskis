package lt.setkus.pliuskis.core.designsystem.component

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import app.cash.paparazzi.Paparazzi
import lt.setkus.pliuskis.core.designsystem.theme.PliuskisTheme
import org.junit.Rule
import org.junit.Test

internal const val ERROR_TITLE = "Something went wrong"
internal const val ERROR_MESSAGE = "We couldn't reach your devices. Check your connection and try again."
internal const val ERROR_ACTION = "Retry"

@Composable
internal fun ErrorScreenUnderTest(darkTheme: Boolean, withAction: Boolean) {
    PliuskisTheme(darkTheme = darkTheme) {
        Surface {
            if (withAction) {
                ErrorScreen(title = ERROR_TITLE, message = ERROR_MESSAGE, actionLabel = ERROR_ACTION, onAction = {})
            } else {
                ErrorScreen(title = ERROR_TITLE, message = ERROR_MESSAGE)
            }
        }
    }
}

/**
 * STORY-1: Generic error screen component in the design system.
 * Rendering of AC1 (icon, title, message) and AC2 (labelled action button).
 */
class Story1ErrorScreenSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi()

    // AC1
    @Test
    fun testStory1_showsIconTitleAndMessage() {
        paparazzi.snapshot { ErrorScreenUnderTest(darkTheme = false, withAction = false) }
    }

    // AC1
    @Test
    fun testStory1_showsIconTitleAndMessageInDarkTheme() {
        paparazzi.snapshot { ErrorScreenUnderTest(darkTheme = true, withAction = false) }
    }

    // AC2
    @Test
    fun testStory1_showsActionButtonWithLabel() {
        paparazzi.snapshot { ErrorScreenUnderTest(darkTheme = false, withAction = true) }
    }
}
