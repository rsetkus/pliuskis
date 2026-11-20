package lt.setkus.pliuskis.core.presentation.control

import lt.setkus.pliuskis.core.systemstate.SystemState
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.math.roundToInt
import kotlin.native.HiddenFromObjC

private const val LOW_HUMIDITY_THRESHOLD = 5f
private const val LOW_WATER_LEVEL_THRESHOLD = 5f

/** The firmware's reading scale: 0 is dry soil or an empty tank, 10 is wet soil or a full tank. */
private const val READING_SCALE_MAX = 10f
private const val PERCENT = 100

/**
 * Applies the status thresholds: a reading below 5 is thirsty or low; 5 itself is not. Also turns each 0..10 reading
 * into a whole percentage, clamped to 0..100. Swift gets the resulting [DeviceStatus] only, so the mapper is hidden
 * from it.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
class DeviceStatusMapper : (SystemState) -> DeviceStatus {

    override fun invoke(input: SystemState) =
        DeviceStatus(
            humidity = mapHumidity(input.humidity),
            waterLevel = mapWaterLevel(input.waterLevel),
            updatedAt = input.timestamp,
            soilMoisturePercent = percent(input.humidity),
            waterLevelPercent = percent(input.waterLevel),
        )

    private fun percent(reading: Float): Int =
        (reading / READING_SCALE_MAX * PERCENT).roundToInt().coerceIn(0, PERCENT)

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
