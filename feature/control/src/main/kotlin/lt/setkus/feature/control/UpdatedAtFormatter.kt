package lt.setkus.feature.control

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Formats a reading's time for the health card: "23:40" for a reading from today, "Nov 19, 23:40" for an older one,
 * in [timeZone] with month names from [locale].
 */
class UpdatedAtFormatter(
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    locale: Locale = Locale.getDefault(),
) : (Instant) -> String {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm", locale)

    override fun invoke(updatedAt: Instant): String {
        val reading = updatedAt.toLocalDateTime(timeZone)
        val today = clock.now().toLocalDateTime(timeZone).date
        val formatter = if (reading.date == today) timeFormatter else dateTimeFormatter
        return reading.toJavaLocalDateTime().format(formatter)
    }
}
