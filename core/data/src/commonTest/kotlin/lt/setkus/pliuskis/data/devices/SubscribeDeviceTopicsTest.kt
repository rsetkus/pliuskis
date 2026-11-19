package lt.setkus.pliuskis.data.devices

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import lt.setkus.pliuskis.core.devicelist.DeviceEvent
import lt.setkus.pliuskis.data.mqttclient.IotMessage
import lt.setkus.pliuskis.data.wire.RecordingIotManager

class SubscribeDeviceTopicsTest : FunSpec({

    test(
        """
        GIVEN retained messages on the device discovery topics
        WHEN devices are subscribed
        THEN should map them to events with the deviceId taken from the topic
    """
    ) {
        val manager = RecordingIotManager(
            clientId = "client-id",
            messagesByTopicFilter = mapOf(
                "devices/+/info" to listOf(IotMessage("devices/abc/info", """{"name":"Basil"}""")),
                "devices/+/availability" to listOf(IotMessage("devices/abc/availability", "online")),
            ),
        )

        SubscribeDeviceTopics(manager).subscribeDevices().toList() shouldContainExactlyInAnyOrder listOf(
            DeviceEvent.Info("abc", """{"name":"Basil"}"""),
            DeviceEvent.Availability("abc", "online")
        )

        // Each discovery topic is subscribed exactly once, and the manager is used for nothing else.
        manager.subscribed shouldContainExactlyInAnyOrder listOf("devices/+/info", "devices/+/availability")
        manager.published.shouldBeEmpty()
        manager.clientIdReads shouldBe 0
    }

    test(
        """
        GIVEN messages on malformed topics
        WHEN devices are subscribed
        THEN should drop them
    """
    ) {
        val manager = RecordingIotManager(
            clientId = "client-id",
            messagesByTopicFilter = mapOf(
                "devices/+/info" to listOf(
                    IotMessage("devices//info", "{}"),
                    IotMessage("devices/abc/info/extra", "{}"),
                    IotMessage("devices/abc/info", "{}")
                ),
                "devices/+/availability" to emptyList(),
            ),
        )

        SubscribeDeviceTopics(manager).subscribeDevices().toList() shouldContainExactlyInAnyOrder listOf(
            DeviceEvent.Info("abc", "{}")
        )
    }
})
