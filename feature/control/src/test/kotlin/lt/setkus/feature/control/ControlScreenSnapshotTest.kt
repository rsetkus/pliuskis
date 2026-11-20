package lt.setkus.feature.control

import app.cash.paparazzi.Paparazzi
import lt.setkus.pliuskis.core.designsystem.component.PliuskisBackground
import lt.setkus.pliuskis.core.designsystem.theme.PliuskisTheme
import lt.setkus.pliuskis.core.presentation.control.ControlUiState
import lt.setkus.pliuskis.core.presentation.control.DeviceStatus
import lt.setkus.pliuskis.core.presentation.control.HumidityStatus
import lt.setkus.pliuskis.core.presentation.control.WaterLevelStatus
import lt.setkus.pliuskis.core.presentation.control.WateringState
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

private val READ_AT = Instant.fromEpochSeconds(1_795_111_368L)

/**
 * Screenshot tests for the control screen, on the app canvas as the app shows it.
 * Record with `./gradlew feature:control:recordPaparazziDebug`, verify with `verifyPaparazziDebug`.
 */
class ControlScreenSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi()

    private val thriving = DeviceStatus(
        humidity = HumidityStatus.GOOD,
        waterLevel = WaterLevelStatus.ENOUGH,
        updatedAt = READ_AT,
        soilMoisturePercent = 72,
        waterLevelPercent = 100,
    )

    private val thirsty = DeviceStatus(
        humidity = HumidityStatus.THIRSTY,
        waterLevel = WaterLevelStatus.LOW,
        updatedAt = READ_AT,
        soilMoisturePercent = 30,
        waterLevelPercent = 20,
    )

    private fun snapshot(
        state: ControlUiState,
        wateringState: WateringState = WateringState.IDLE,
        darkTheme: Boolean = false,
    ) {
        paparazzi.snapshot {
            PliuskisTheme(darkTheme = darkTheme) {
                PliuskisBackground {
                    ControlScreen(
                        deviceName = "Basil",
                        controlUiState = state,
                        wateringState = wateringState,
                        onBackClick = {},
                        onWaterButtonClick = {},
                        onRetry = {},
                        updatedAtText = { "23:40" }
                    )
                }
            }
        }
    }

    @Test
    fun thrivingPlant() {
        snapshot(ControlUiState.Success(thriving))
    }

    @Test
    fun thrivingPlantInDarkTheme() {
        snapshot(ControlUiState.Success(thriving), darkTheme = true)
    }

    @Test
    fun thirstyPlantWhileWatering() {
        snapshot(ControlUiState.Success(thirsty), wateringState = WateringState.SENDING)
    }

    @Test
    fun wateringStarted() {
        snapshot(ControlUiState.Success(thirsty), wateringState = WateringState.STARTED)
    }

    @Test
    fun wateringFailed() {
        snapshot(ControlUiState.Success(thirsty), wateringState = WateringState.FAILED)
    }

    @Test
    fun connectionError() {
        snapshot(ControlUiState.Error("socket closed"))
    }
}
