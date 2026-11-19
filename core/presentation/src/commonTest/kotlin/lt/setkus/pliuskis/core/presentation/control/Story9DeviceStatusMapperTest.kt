package lt.setkus.pliuskis.core.presentation.control

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import lt.setkus.pliuskis.core.systemstate.SystemState
import kotlin.time.Instant

// Today's thresholds: a reading below 5 is low; 5 itself is not.
private const val THRESHOLD = 5f
private const val BELOW_THRESHOLD = 4.99f
private const val ABOVE_THRESHOLD = 5.01f
private const val EMPTY = 0f
private const val PLENTY = 70f

// 2026-11-19T18:02:48Z
private val READ_AT = Instant.fromEpochSeconds(1_795_111_368L)

private fun reading(humidity: Float = PLENTY, waterLevel: Float = PLENTY) =
    SystemState(humidity = humidity, waterLevel = waterLevel, timestamp = READ_AT)

/**
 * STORY-9: the shared status logic turns a reading into its meaning and the reading time, with no display text.
 * Covers AC43.
 */
class Story9DeviceStatusMapperTest : FunSpec({

    val target = DeviceStatusMapper()

    // AC43
    // GIVEN humidity below the threshold
    // WHEN the reading is mapped
    // THEN the plant is thirsty
    test("testStory9_humidityBelowThresholdIsThirsty") {
        target(reading(humidity = BELOW_THRESHOLD)).humidity shouldBe HumidityStatus.THIRSTY
    }

    // AC43
    // GIVEN humidity of 0
    // WHEN the reading is mapped
    // THEN the plant is thirsty
    test("testStory9_zeroHumidityIsThirsty") {
        target(reading(humidity = EMPTY)).humidity shouldBe HumidityStatus.THIRSTY
    }

    // AC43
    // GIVEN humidity exactly at the threshold
    // WHEN the reading is mapped
    // THEN the plant is good (the threshold itself is not thirsty)
    test("testStory9_humidityAtThresholdIsGood") {
        target(reading(humidity = THRESHOLD)).humidity shouldBe HumidityStatus.GOOD
    }

    // AC43
    // GIVEN humidity above the threshold
    // WHEN the reading is mapped
    // THEN the plant is good
    test("testStory9_humidityAboveThresholdIsGood") {
        target(reading(humidity = ABOVE_THRESHOLD)).humidity shouldBe HumidityStatus.GOOD
    }

    // AC43
    // GIVEN a water level below the threshold
    // WHEN the reading is mapped
    // THEN the water level is low
    test("testStory9_waterLevelBelowThresholdIsLow") {
        target(reading(waterLevel = BELOW_THRESHOLD)).waterLevel shouldBe WaterLevelStatus.LOW
    }

    // AC43
    // GIVEN a water level of 0
    // WHEN the reading is mapped
    // THEN the water level is low
    test("testStory9_zeroWaterLevelIsLow") {
        target(reading(waterLevel = EMPTY)).waterLevel shouldBe WaterLevelStatus.LOW
    }

    // AC43
    // GIVEN a water level exactly at the threshold
    // WHEN the reading is mapped
    // THEN there is enough water (the threshold itself is not low)
    test("testStory9_waterLevelAtThresholdIsEnough") {
        target(reading(waterLevel = THRESHOLD)).waterLevel shouldBe WaterLevelStatus.ENOUGH
    }

    // AC43
    // GIVEN a water level above the threshold
    // WHEN the reading is mapped
    // THEN there is enough water
    test("testStory9_waterLevelAboveThresholdIsEnough") {
        target(reading(waterLevel = ABOVE_THRESHOLD)).waterLevel shouldBe WaterLevelStatus.ENOUGH
    }

    // AC43
    // GIVEN humidity and water level judged independently of each other
    // WHEN a reading with low humidity and enough water is mapped
    // THEN the status holds exactly the two meanings and the reading Instant, nothing else
    test("testStory9_statusHoldsBothMeaningsAndReadingInstant") {
        target(reading(humidity = BELOW_THRESHOLD, waterLevel = THRESHOLD)) shouldBe
            DeviceStatus(
                humidity = HumidityStatus.THIRSTY,
                waterLevel = WaterLevelStatus.ENOUGH,
                updatedAt = READ_AT,
            )
    }

    // AC43
    // GIVEN good humidity and a low water level
    // WHEN the reading is mapped
    // THEN the status holds GOOD, LOW and the reading Instant
    test("testStory9_goodHumidityWithLowWaterKeepsReadingInstant") {
        target(reading(humidity = ABOVE_THRESHOLD, waterLevel = BELOW_THRESHOLD)) shouldBe
            DeviceStatus(
                humidity = HumidityStatus.GOOD,
                waterLevel = WaterLevelStatus.LOW,
                updatedAt = READ_AT,
            )
    }
})
