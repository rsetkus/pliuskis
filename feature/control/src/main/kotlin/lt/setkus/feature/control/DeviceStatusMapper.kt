package lt.setkus.feature.control

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import lt.setkus.pliuskis.core.systemstate.SystemState
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val PATTERN_FORMAT = "MMMM d EEEE, yyyy HH:mm:ss"
private const val LOW_HUMIDITY_THRESHOLD = 5f
private const val LOW_WATER_LEVEL_THRESHOLD = 5f

class DeviceStatusMapper : (SystemState) -> DeviceStatus {

    private val formatter = DateTimeFormatter
        .ofPattern(PATTERN_FORMAT, Locale.getDefault())

    override fun invoke(input: SystemState) =
        DeviceStatus(
            mapHumidity(input.humidity),
            mapWaterLevel(input.waterLevel),
            mapTimestamp(input.timestamp)
        )

    private fun mapHumidity(level: Float) = if (level < LOW_HUMIDITY_THRESHOLD) {
        "I am thirsty! Please water to stay alive."
    } else {
        "I am good. Enough water to be alive."
    }

    private fun mapWaterLevel(level: Float) = if (level < LOW_WATER_LEVEL_THRESHOLD) {
        "Running low of water."
    } else {
        "More than enough water."
    }

    private fun mapTimestamp(timestamp: Instant): String {
        val zoneId = TimeZone.currentSystemDefault()
        val localDateTime = timestamp.toLocalDateTime(zoneId)

        val formatted = localDateTime.toJavaLocalDateTime().format(formatter)

        return "Updated at $formatted."
    }

}