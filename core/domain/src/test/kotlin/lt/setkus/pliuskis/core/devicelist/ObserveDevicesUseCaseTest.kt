package lt.setkus.pliuskis.core.devicelist

import app.cash.turbine.test
import arrow.core.Either
import arrow.core.right
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import lt.setkus.pliuskis.core.devicelist.DeviceEvent.Availability
import lt.setkus.pliuskis.core.devicelist.DeviceEvent.Info

private fun infoJson(deviceId: String, name: String) =
    """{"name":"$name","deviceId":"$deviceId","type":"plant"}"""

private fun device(deviceId: String, name: String, online: Boolean) =
    Device(DeviceDomainModel(name, deviceId, DeviceType.PLANT), online)

class ObserveDevicesUseCaseTest : FunSpec({

    fun useCaseFor(vararg events: DeviceEvent): ObserveDevicesUseCase {
        val subscribable = mockk<DeviceSubscribable>()
        every { subscribable.subscribeDevices() } returns flowOf(*events)
        return ObserveDevicesUseCase(subscribable)
    }

    test("""
        GIVEN a device's retained info and online availability
        WHEN devices are observed
        THEN should emit the device as online
    """) {
        useCaseFor(Info("a", infoJson("a", "Basil")), Availability("a", "online"))(Unit).test {
            awaitItem() shouldBe listOf(device("a", "Basil", online = false)).right()
            awaitItem() shouldBe listOf(device("a", "Basil", online = true)).right()
            awaitComplete()
        }
    }

    test("""
        GIVEN an online device
        WHEN the broker publishes its Last Will
        THEN should emit the device as offline
    """) {
        val target = useCaseFor(
            Info("a", infoJson("a", "Basil")),
            Availability("a", "online"),
            Availability("a", "offline")
        )
        target(Unit).test {
            skipItems(2)
            awaitItem() shouldBe listOf(device("a", "Basil", online = false)).right()
            awaitComplete()
        }
    }

    test("""
        GIVEN a malformed info payload followed by a valid one
        WHEN devices are observed
        THEN should skip the malformed device and keep emitting
    """) {
        useCaseFor(Info("bad", "not json"), Info("a", infoJson("a", "Basil")))(Unit).test {
            awaitItem() shouldBe emptyList<Device>().right()
            awaitItem() shouldBe listOf(device("a", "Basil", online = false)).right()
            awaitComplete()
        }
    }

    test("""
        GIVEN a known device
        WHEN an empty retained info payload arrives
        THEN should remove the device
    """) {
        useCaseFor(Info("a", infoJson("a", "Basil")), Info("a", ""))(Unit).test {
            skipItems(1)
            awaitItem() shouldBe emptyList<Device>().right()
            awaitComplete()
        }
    }

    test("""
        GIVEN availability that arrives before the device's info
        WHEN the info arrives
        THEN should apply the earlier availability
    """) {
        useCaseFor(Availability("a", "online"), Info("a", infoJson("a", "Basil")))(Unit).test {
            awaitItem() shouldBe emptyList<Device>().right()
            awaitItem() shouldBe listOf(device("a", "Basil", online = true)).right()
            awaitComplete()
        }
    }

    test("""
        GIVEN several devices
        WHEN devices are observed
        THEN should emit them sorted by name
    """) {
        useCaseFor(Info("b", infoJson("b", "Tomato")), Info("a", infoJson("a", "Basil")))(Unit).test {
            skipItems(1)
            awaitItem() shouldBe listOf(
                device("a", "Basil", online = false),
                device("b", "Tomato", online = false)
            ).right()
            awaitComplete()
        }
    }

    test("""
        GIVEN an info payload whose deviceId differs from its topic
        WHEN devices are observed
        THEN should use the deviceId from the topic
    """) {
        useCaseFor(Info("topic-id", infoJson("payload-id", "Basil")))(Unit).test {
            awaitItem() shouldBe listOf(device("topic-id", "Basil", online = false)).right()
            awaitComplete()
        }
    }

    test("""
        GIVEN the subscription itself fails
        WHEN devices are observed
        THEN should emit the error
    """) {
        val subscribable = mockk<DeviceSubscribable>()
        every { subscribable.subscribeDevices() } returns flow { throw IllegalStateException("disconnected") }
        ObserveDevicesUseCase(subscribable)(Unit).test {
            awaitItem().shouldBeInstanceOf<Either.Left<Throwable>>()
            awaitComplete()
        }
    }
})
