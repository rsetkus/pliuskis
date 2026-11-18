package lt.setkus.pliuskis.core.wire

import arrow.core.right
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import lt.setkus.pliuskis.core.devicelist.Device
import lt.setkus.pliuskis.core.devicelist.DeviceDomainModel
import lt.setkus.pliuskis.core.devicelist.DeviceEvent
import lt.setkus.pliuskis.core.devicelist.DeviceSubscribable
import lt.setkus.pliuskis.core.devicelist.DeviceType
import lt.setkus.pliuskis.core.devicelist.ObserveDevicesUseCase

/**
 * STORY-1 AC2-AC4: pins how a retained `devices/{id}/info` payload decodes into [DeviceDomainModel],
 * through the decoder the app actually uses ([ObserveDevicesUseCase]).
 * AC3 and AC4 pin today's behaviour: an unknown `type` becomes [DeviceType.UNKNOWN], and unknown keys
 * are ignored.
 */
class Story1DiscoveryInfoDecodingTest : FunSpec({

    class FakeDiscoverySource(private val payload: String) : DeviceSubscribable {
        override fun subscribeDevices(): Flow<DeviceEvent> =
            flowOf(DeviceEvent.Info(DomainWireFixtures.DEVICE_ID, payload))
    }

    suspend fun decode(payload: String) =
        ObserveDevicesUseCase(FakeDiscoverySource(payload)).invoke(Unit).toList()

    fun onlyDevice(type: DeviceType) =
        listOf(listOf(Device(DeviceDomainModel("Basil", "pico-1", type), online = false)).right())

    test("testStory1_infoPayloadDecodesNameDeviceIdAndPlantType") {
        decode(DomainWireFixtures.INFO_PAYLOAD) shouldBe onlyDevice(DeviceType.PLANT)
    }

    test("testStory1_infoPayloadWithUnknownTypeDecodesToUnknownType") {
        decode(DomainWireFixtures.INFO_PAYLOAD_UNKNOWN_TYPE) shouldBe onlyDevice(DeviceType.UNKNOWN)
    }

    test("testStory1_infoPayloadWithExtraUnknownKeyIgnoresTheKey") {
        decode(DomainWireFixtures.INFO_PAYLOAD_EXTRA_KEY) shouldBe onlyDevice(DeviceType.PLANT)
    }
})
