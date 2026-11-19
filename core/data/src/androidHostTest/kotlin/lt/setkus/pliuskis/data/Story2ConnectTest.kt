package lt.setkus.pliuskis.data

import app.cash.turbine.test
import arrow.core.Either
import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAckReturnCode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import lt.setkus.pliuskis.core.connect.ConnectUseCase
import lt.setkus.pliuskis.data.mqttclient.FakeMqtt3BlockingClient
import lt.setkus.pliuskis.data.mqttclient.HiveMqttClientManager

/**
 * STORY-2: DevicesViewModel connects then discovers; app launch no longer connects.
 * Covers AC7 through the real connect chain: ConnectUseCase -> ConnectMqttSource -> HiveMqttClientManager.
 */
class Story2ConnectTest : FunSpec({

    // AC7
    // GIVEN the broker answers the connect with a non-success CONNACK
    // WHEN the connect use case is invoked
    // THEN should emit a Left, not a Right
    test("testStory2_rejectedConnAckEmitsLeft") {
        val client = FakeMqtt3BlockingClient(MqttClientState.DISCONNECTED, Mqtt3ConnAckReturnCode.NOT_AUTHORIZED)
        val manager = HiveMqttClientManager(client)
        val target = ConnectUseCase(ConnectMqttSource(manager))

        target(Unit).test {
            awaitItem().shouldBeInstanceOf<Either.Left<Throwable>>()
            awaitComplete()
        }
        client.connectCalls shouldBe 1
    }
})
