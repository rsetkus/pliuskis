package lt.setkus.pliuskis.data.wire

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import lt.setkus.pliuskis.data.devices.SubscribeDeviceTopics
import lt.setkus.pliuskis.data.systemstate.SubscribeSystemStatusTopic

/**
 * STORY-1 AC7-AC8: pins the exact topics and topic filters the app subscribes to.
 */
class Story1SubscriptionTopicsTest : FunSpec({

    test("testStory1_systemStatusSubscriptionUsesApplicationClientDeviceStatusTopic") {
        val manager = RecordingIotManager(DataWireFixtures.CLIENT_ID)

        SubscribeSystemStatusTopic(manager).subscribeState(DataWireFixtures.DEVICE_ID).toList()

        manager.subscribed shouldBe listOf(DataWireFixtures.SYSTEM_STATUS_TOPIC)
    }

    test("testStory1_discoverySubscriptionUsesExactlyInfoAndAvailabilityFilters") {
        val manager = RecordingIotManager(DataWireFixtures.CLIENT_ID)

        SubscribeDeviceTopics(manager).subscribeDevices().toList()

        manager.subscribed shouldContainExactlyInAnyOrder listOf(
            DataWireFixtures.INFO_TOPIC_FILTER,
            DataWireFixtures.AVAILABILITY_TOPIC_FILTER
        )
    }
})
