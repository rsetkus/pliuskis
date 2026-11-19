package lt.setkus.pliuskis.data.wire

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import lt.setkus.pliuskis.data.command.water.MqttExecuteWaterCommand
import lt.setkus.pliuskis.data.systemstate.AwsSystemStateRequest

/**
 * STORY-1 AC5-AC6: pins the exact JSON and topic of every message the app publishes to a device.
 */
class Story1OutgoingPayloadTest : FunSpec({

    test("testStory1_statusRequestPublishesApplicationIdToDeviceStatusTopic") {
        val manager = RecordingIotManager(DataWireFixtures.CLIENT_ID)

        AwsSystemStateRequest(manager).requestSystemState(DataWireFixtures.DEVICE_ID)

        manager.published shouldBe listOf(
            PublishedMessage(DataWireFixtures.STATUS_REQUEST_TOPIC, DataWireFixtures.STATUS_REQUEST_PAYLOAD)
        )
    }

    test("testStory1_waterCommandPublishesWaterCommandToDeviceCommandTopic") {
        val manager = RecordingIotManager(DataWireFixtures.CLIENT_ID)

        MqttExecuteWaterCommand(manager).execute(DataWireFixtures.DEVICE_ID)

        manager.published shouldBe listOf(
            PublishedMessage(DataWireFixtures.WATER_COMMAND_TOPIC, DataWireFixtures.WATER_COMMAND_PAYLOAD)
        )
    }
})
