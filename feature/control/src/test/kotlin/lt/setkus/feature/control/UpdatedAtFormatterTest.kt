package lt.setkus.feature.control

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.TimeZone
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant

private val NOW = Instant.parse("2026-11-19T20:40:00Z")

private val fixedClock = object : Clock {
    override fun now() = NOW
}

class UpdatedAtFormatterTest : FunSpec({

    val target = UpdatedAtFormatter(clock = fixedClock, timeZone = TimeZone.UTC, locale = Locale.US)

    test("GIVEN a reading from today WHEN formatted THEN only the time shows") {
        target(Instant.parse("2026-11-19T08:05:00Z")) shouldBe "08:05"
    }

    test("GIVEN a reading from another day WHEN formatted THEN the date and time show") {
        target(Instant.parse("2026-11-18T23:40:00Z")) shouldBe "Nov 18, 23:40"
    }

    test("GIVEN a time zone where the reading falls on today WHEN formatted THEN only the time shows") {
        val vilnius = UpdatedAtFormatter(
            clock = fixedClock,
            timeZone = TimeZone.of("Europe/Vilnius"),
            locale = Locale.US,
        )

        vilnius(Instant.parse("2026-11-18T23:40:00Z")) shouldBe "01:40"
    }
})
