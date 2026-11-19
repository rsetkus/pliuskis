package lt.setkus.pliuskis.core.presentation.control

import lt.setkus.pliuskis.core.systemstate.SystemState
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

private const val LOW_HUMIDITY_THRESHOLD = 5f
private const val LOW_WATER_LEVEL_THRESHOLD = 5f

/**
 * Applies the status thresholds: a reading below 5 is thirsty or low; 5 itself is not. Swift gets the resulting
 * [DeviceStatus] only, so the mapper is hidden from it.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
class DeviceStatusMapper : (SystemState) -> DeviceStatus {

    override fun invoke(input: SystemState) =
        DeviceStatus(
            humidity = mapHumidity(input.humidity),
            waterLevel = mapWaterLevel(input.waterLevel),
            updatedAt = input.timestamp,
        )

    private fun mapHumidity(level: Float) = if (level < LOW_HUMIDITY_THRESHOLD) {
        HumidityStatus.THIRSTY
    } else {
        HumidityStatus.GOOD
    }

    private fun mapWaterLevel(level: Float) = if (level < LOW_WATER_LEVEL_THRESHOLD) {
        WaterLevelStatus.LOW
    } else {
        WaterLevelStatus.ENOUGH
    }
}
