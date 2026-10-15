package lt.setkus.pliuskis.core.designsystem.component

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

private const val LARGE_FONT_SCALE = 1.5f

/**
 * STORY-1: Generic error screen component in the design system.
 * Rendering of AC1 (icon, title, message) at font scale 1.5.
 */
class Story1ErrorScreenLargeFontSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(fontScale = LARGE_FONT_SCALE))

    // AC1
    @Test
    fun testStory1_showsIconTitleAndMessageAtLargeFontScale() {
        paparazzi.snapshot { ErrorScreenUnderTest(darkTheme = false, withAction = true) }
    }
}
