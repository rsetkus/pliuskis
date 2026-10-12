package lt.setkus.pliuskis.data.devices

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import lt.setkus.pliuskis.core.devicelist.DeviceEvent
import lt.setkus.pliuskis.data.IotManager
import lt.setkus.pliuskis.data.mqttclient.IotMessage

class SubscribeDeviceTopicsTest : FunSpec({

    test("""
        GIVEN retained messages on the device discovery topics
        WHEN devices are subscribed
        THEN should map them to events with the deviceId taken from the topic
    """) {
        val manager = mockk<IotManager>()
        every { manager.subscribe("devices/+/info") } returns flowOf(
            IotMessage("devices/abc/info", """{"name":"Basil"}""")
        )
        every { manager.subscribe("devices/+/availability") } returns flowOf(
            IotMessage("devices/abc/availability", "online")
        )

        SubscribeDeviceTopics(manager).subscribeDevices().toList() shouldContainExactlyInAnyOrder listOf(
            DeviceEvent.Info("abc", """{"name":"Basil"}"""),
            DeviceEvent.Availability("abc", "online")
        )

        verify(exactly = 1) { manager.subscribe("devices/+/info") }
        verify(exactly = 1) { manager.subscribe("devices/+/availability") }
        confirmVerified(manager)
    }

    test("""
        GIVEN messages on malformed topics
        WHEN devices are subscribed
        THEN should drop them
    """) {
        val manager = mockk<IotManager>()
        every { manager.subscribe("devices/+/info") } returns flowOf(
            IotMessage("devices//info", "{}"),
            IotMessage("devices/abc/info/extra", "{}"),
            IotMessage("devices/abc/info", "{}")
        )
        every { manager.subscribe("devices/+/availability") } returns flowOf()

        SubscribeDeviceTopics(manager).subscribeDevices().toList() shouldContainExactlyInAnyOrder listOf(
            DeviceEvent.Info("abc", "{}")
        )
    }
})
