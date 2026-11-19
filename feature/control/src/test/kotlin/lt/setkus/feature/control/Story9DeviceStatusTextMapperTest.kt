package lt.setkus.feature.control

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import lt.setkus.pliuskis.core.presentation.control.DeviceStatus
import lt.setkus.pliuskis.core.presentation.control.DeviceStatusMapper
import lt.setkus.pliuskis.core.presentation.control.HumidityStatus
import lt.setkus.pliuskis.core.presentation.control.WaterLevelStatus
import lt.setkus.pliuskis.core.systemstate.SystemState
import java.util.Locale
import kotlin.time.Instant

private const val THIRSTY_TEXT = "I am thirsty! Please water to stay alive."
private const val GOOD_TEXT = "I am good. Enough water to be alive."
private const val LOW_WATER_TEXT = "Running low of water."
private const val ENOUGH_WATER_TEXT = "More than enough water."

private val UTC = TimeZone.UTC
private val VILNIUS = TimeZone.of("Europe/Vilnius")

private val NOVEMBER_EVENING = LocalDateTime(2026, 11, 19, 18, 2, 48).toInstant(UTC)
private val MARCH_MORNING = LocalDateTime(2026, 3, 5, 7, 4, 9).toInstant(UTC)

private fun status(
    humidity: HumidityStatus = HumidityStatus.GOOD,
    waterLevel: WaterLevelStatus = WaterLevelStatus.ENOUGH,
    updatedAt: Instant = NOVEMBER_EVENING,
) = DeviceStatus(humidity = humidity, waterLevel = waterLevel, updatedAt = updatedAt)

/**
 * STORY-9: the Android mapping turns the shared status into today's display text and date format
 * ("MMMM d EEEE, yyyy HH:mm:ss" in the given locale and time zone).
 * Covers AC44.
 */
class Story9DeviceStatusTextMapperTest : FunSpec({

    val usInUtc = DeviceStatusTextMapper(timeZone = UTC, locale = Locale.US)

    // AC44
    // GIVEN a thirsty plant with a low water level
    // WHEN the status is mapped for display
    // THEN the texts are today's thirsty and low-water texts
    test("testStory9_thirstyAndLowWaterShowTodaysTexts") {
        usInUtc(status(HumidityStatus.THIRSTY, WaterLevelStatus.LOW)) shouldBe
            DeviceStatusText(
                humidity = THIRSTY_TEXT,
                waterLevel = LOW_WATER_TEXT,
                updatedTime = "Updated at November 19 Thursday, 2026 18:02:48.",
            )
    }

    // AC44
    // GIVEN a good plant with enough water
    // WHEN the status is mapped for display
    // THEN the texts are today's good and enough-water texts
    test("testStory9_goodAndEnoughWaterShowTodaysTexts") {
        usInUtc(status(HumidityStatus.GOOD, WaterLevelStatus.ENOUGH)) shouldBe
            DeviceStatusText(
                humidity = GOOD_TEXT,
                waterLevel = ENOUGH_WATER_TEXT,
                updatedTime = "Updated at November 19 Thursday, 2026 18:02:48.",
            )
    }

    // AC44
    // GIVEN a reading at 07:04:09 UTC on 5 March
    // WHEN it is formatted in UTC with the US locale
    // THEN the day has no leading zero and the hour has one
    test("testStory9_singleDigitDayAndHourUseTodaysPattern") {
        usInUtc(status(updatedAt = MARCH_MORNING)).updatedTime shouldBe
            "Updated at March 5 Thursday, 2026 07:04:09."
    }

    // AC44
    // GIVEN a reading at 18:02:48 UTC
    // WHEN it is formatted in the Europe/Vilnius time zone (UTC+2 in November)
    // THEN the time is shown in that zone
    test("testStory9_updatedTimeUsesGivenTimeZone") {
        DeviceStatusTextMapper(timeZone = VILNIUS, locale = Locale.US)(status()).updatedTime shouldBe
            "Updated at November 19 Thursday, 2026 20:02:48."
    }

    // AC44
    // GIVEN a reading on 5 March
    // WHEN it is formatted with the German locale
    // THEN the month and weekday names come from that locale
    test("testStory9_updatedTimeUsesGivenLocale") {
        DeviceStatusTextMapper(timeZone = UTC, locale = Locale.GERMANY)(status(updatedAt = MARCH_MORNING))
            .updatedTime shouldBe "Updated at März 5 Donnerstag, 2026 07:04:09."
    }

    // AC44
    // GIVEN the same SystemState input as today's DeviceStatusMapper got (humidity at the threshold, water just below)
    // WHEN it goes through the shared status logic and then the Android mapping
    // THEN the display text is what today's mapper produced
    test("testStory9_systemStateThroughSharedAndAndroidMappingMatchesToday") {
        val reading = SystemState(humidity = 5f, waterLevel = 4.99f, timestamp = NOVEMBER_EVENING)

        usInUtc(DeviceStatusMapper()(reading)) shouldBe
            DeviceStatusText(
                humidity = GOOD_TEXT,
                waterLevel = LOW_WATER_TEXT,
                updatedTime = "Updated at November 19 Thursday, 2026 18:02:48.",
            )
    }
})
