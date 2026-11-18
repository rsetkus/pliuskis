package lt.setkus.pliuskis.core.wire

import arrow.core.Either
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import lt.setkus.pliuskis.core.systemstate.GetSystemStatusUseUseCase
import lt.setkus.pliuskis.core.systemstate.SystemState
import lt.setkus.pliuskis.core.systemstate.SystemStateSubscribable

/**
 * STORY-1 AC1: pins how the status payload from the device decodes into [SystemState].
 * The timestamp is checked through its epoch second and nanosecond parts only, so the test does not
 * depend on which package `Instant` lives in.
 */
class Story1StatusPayloadDecodingTest : FunSpec({

    class FakeStatusSource(private val payload: String) : SystemStateSubscribable {
        override fun subscribeState(deviceId: String): Flow<String> = flowOf(payload)
    }

    test("testStory1_statusPayloadDecodesHumidityWaterLevelAndTimestamp") {
        val results = GetSystemStatusUseUseCase(FakeStatusSource(DomainWireFixtures.STATUS_PAYLOAD))
            .invoke(DomainWireFixtures.DEVICE_ID)
            .toList()

        results shouldHaveSize 1
        val state = results.single().shouldBeInstanceOf<Either.Right<SystemState>>().value
        state.humidity shouldBe 55f
        state.waterLevel shouldBe 70f
        state.timestamp.epochSeconds shouldBe 1_700_000_000L
        state.timestamp.nanosecondsOfSecond shouldBe 5
    }
})
