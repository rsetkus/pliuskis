package lt.setkus.pliuskis.data.clientid

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import lt.setkus.pliuskis.data.mqttclient.HiveMqttClientManager
import lt.setkus.pliuskis.data.mqttclient.MqttSettings
import lt.setkus.pliuskis.data.mqttclient.TlsMode
import lt.setkus.pliuskis.data.mqttclient.createMqttClient
import lt.setkus.pliuskis.data.systemstate.SubscribeSystemStatusTopic
import lt.setkus.pliuskis.data.wire.RecordingIotManager

/** The debug client (local broker, no TLS) that the debug build creates for [clientId]. */
private fun debugClient(clientId: String) =
    createMqttClient(MqttSettings(clientId, "10.0.2.2", 1883, TlsMode.NONE), null)

/**
 * STORY-5 AC22-AC23: the stored client ID is the identifier of the HiveMQ client, so `IotManager.getClientId()`
 * returns it, and it is the applicationId in the status topic `application/{clientId}/{deviceId}/status`.
 *
 * The chain under test: [ClientIdStore] -> [ClientIdProvider] -> [createMqttClient] -> [HiveMqttClientManager].
 * Building the HiveMQ client does not connect, so no broker is needed.
 */
class Story5ClientIdTopicTest : FunSpec({

    // AC22
    // GIVEN the stored client ID abc123
    // WHEN the MQTT client is built with the provider's ID
    // THEN IotManager.getClientId() returns abc123
    test("testStory5_iotManagerReturnsStoredClientId") {
        val clientId = ClientIdProvider(InMemoryClientIdStore("abc123")).clientId()

        val manager = HiveMqttClientManager(debugClient(clientId))

        manager.getClientId() shouldBe "abc123"
    }

    // AC22
    // GIVEN a fresh install (nothing stored)
    // WHEN the MQTT client is built with the provider's ID
    // THEN IotManager.getClientId() returns the ID that was stored, not a hardcoded one
    test("testStory5_iotManagerReturnsClientIdStoredOnFirstLaunch") {
        val store = InMemoryClientIdStore()
        val clientId = ClientIdProvider(store).clientId()

        val manager = HiveMqttClientManager(debugClient(clientId))

        manager.getClientId() shouldBe store.stored
    }

    // AC23
    // GIVEN the stored client ID abc123 and device pico-1
    // WHEN the app subscribes to the device status
    // THEN it subscribes to application/abc123/pico-1/status
    test("testStory5_statusSubscriptionUsesStoredClientId") {
        val clientId = ClientIdProvider(InMemoryClientIdStore("abc123")).clientId()
        val appClientId = HiveMqttClientManager(debugClient(clientId)).getClientId()
        val recorder = RecordingIotManager(appClientId)

        SubscribeSystemStatusTopic(recorder).subscribeState("pico-1").toList()

        recorder.subscribed shouldBe listOf("application/abc123/pico-1/status")
    }
})
