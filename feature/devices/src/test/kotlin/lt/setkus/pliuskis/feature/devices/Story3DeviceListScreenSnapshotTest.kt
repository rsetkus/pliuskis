package lt.setkus.pliuskis.feature.devices

import androidx.compose.material3.Surface
import app.cash.paparazzi.Paparazzi
import lt.setkus.pliuskis.core.designsystem.theme.PliuskisTheme
import lt.setkus.pliuskis.feature.devices.DevicesListScreenState.Error
import lt.setkus.pliuskis.feature.devices.DevicesListScreenState.Success
import org.junit.Rule
import org.junit.Test

/**
 * STORY-3: Device list screen shows the generic error screen on error.
 * Rendering of AC13 (error), AC14 (device cards) and AC15 (empty list). Loading (AC16) animates, so
 * it is covered by the behaviour test only.
 */
class Story3DeviceListScreenSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi()

    private fun snapshot(state: DevicesListScreenState) {
        paparazzi.snapshot {
            PliuskisTheme(darkTheme = false) {
                Surface { DeviceListScreen(onDeviceClick = {}, devices = state, onRetry = {}) }
            }
        }
    }

    // AC13
    @Test
    fun testStory3_errorStateShowsGenericErrorScreen() {
        snapshot(Error(Throwable("socket closed")))
    }

    // AC14
    @Test
    fun testStory3_devicesStateShowsDeviceCards() {
        snapshot(
            Success(
                listOf(
                    DeviceListItem(name = "Alpha", id = "a", icon = R.drawable.ic_plant, online = true),
                    DeviceListItem(name = "Beta", id = "b", icon = R.drawable.ic_unknown, online = false)
                )
            )
        )
    }

    // AC15
    @Test
    fun testStory3_emptyStateShowsNoDevicesMessage() {
        snapshot(Success(emptyList()))
    }
}
