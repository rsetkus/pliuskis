package lt.setkus.feature.control

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import lt.setkus.pliuskis.core.presentation.control.DeviceStatus
import lt.setkus.pliuskis.core.presentation.control.HumidityStatus
import lt.setkus.pliuskis.core.presentation.control.WaterLevelStatus
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant

private const val PATTERN_FORMAT = "MMMM d EEEE, yyyy HH:mm:ss"

/**
 * Turns the shared [DeviceStatus] into the Android display text. The reading time is shown in [timeZone], with month
 * and weekday names from [locale].
 */
class DeviceStatusTextMapper(
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    locale: Locale = Locale.getDefault(),
) : (DeviceStatus) -> DeviceStatusText {

    private val formatter = DateTimeFormatter.ofPattern(PATTERN_FORMAT, locale)

    override fun invoke(input: DeviceStatus) =
        DeviceStatusText(
            humidity = humidityText(input.humidity),
            waterLevel = waterLevelText(input.waterLevel),
            updatedTime = updatedTimeText(input.updatedAt)
        )

    private fun humidityText(status: HumidityStatus) = when (status) {
        HumidityStatus.THIRSTY -> "I am thirsty! Please water to stay alive."
        HumidityStatus.GOOD -> "I am good. Enough water to be alive."
    }

    private fun waterLevelText(status: WaterLevelStatus) = when (status) {
        WaterLevelStatus.LOW -> "Running low of water."
        WaterLevelStatus.ENOUGH -> "More than enough water."
    }

    private fun updatedTimeText(timestamp: Instant): String {
        val formatted = timestamp.toLocalDateTime(timeZone).toJavaLocalDateTime().format(formatter)
        return "Updated at $formatted."
    }
}
