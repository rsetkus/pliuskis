package lt.setkus.pliuskis.feature.devices

import app.cash.paparazzi.Paparazzi
import lt.setkus.pliuskis.core.designsystem.component.PliuskisBackground
import lt.setkus.pliuskis.core.designsystem.theme.PliuskisTheme
import lt.setkus.pliuskis.core.devicelist.DeviceType
import lt.setkus.pliuskis.core.presentation.devices.DeviceListItem
import lt.setkus.pliuskis.core.presentation.devices.DevicesListScreenState
import lt.setkus.pliuskis.core.presentation.devices.DevicesListScreenState.Error
import lt.setkus.pliuskis.core.presentation.devices.DevicesListScreenState.Success
import org.junit.Rule
import org.junit.Test

/**
 * Screenshot tests for the device list states, on the app canvas as the app shows them.
 * Record with `./gradlew feature:devices:recordPaparazziDebug`, verify with `verifyPaparazziDebug`.
 */
class Story3DeviceListScreenSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi()

    private val devices = Success(
        listOf(
            DeviceListItem(name = "Basil", id = "a", type = DeviceType.PLANT, online = true),
            DeviceListItem(name = "Tomato", id = "b", type = DeviceType.UNKNOWN, online = false)
        )
    )

    private fun snapshot(state: DevicesListScreenState, darkTheme: Boolean = false) {
        paparazzi.snapshot {
            PliuskisTheme(darkTheme = darkTheme) {
                PliuskisBackground { DeviceListScreen(onDeviceClick = { _, _ -> }, devices = state, onRetry = {}) }
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
        snapshot(devices)
    }

    @Test
    fun testStory3_devicesStateShowsDeviceCardsInDarkTheme() {
        snapshot(devices, darkTheme = true)
    }

    // AC15
    @Test
    fun testStory3_emptyStateShowsNoDevicesMessage() {
        snapshot(Success(emptyList()))
    }
}
