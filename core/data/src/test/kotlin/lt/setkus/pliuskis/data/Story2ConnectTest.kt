package lt.setkus.pliuskis.data

import app.cash.turbine.test
import arrow.core.Either
import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientConfig
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAckReturnCode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import lt.setkus.pliuskis.core.connect.ConnectUseCase
import lt.setkus.pliuskis.data.mqttclient.HiveMqttClientManager

/**
 * STORY-2: DevicesViewModel connects then discovers; app launch no longer connects.
 * Covers AC7 through the real connect chain: ConnectUseCase -> ConnectMqttSource -> HiveMqttClientManager.
 */
class Story2ConnectTest : FunSpec({

    fun clientRespondingWith(returnCode: Mqtt3ConnAckReturnCode): Mqtt3Client {
        val connAck = mockk<Mqtt3ConnAck>()
        every { connAck.returnCode } returns returnCode

        val blocking = mockk<Mqtt3BlockingClient>(relaxed = true)
        every { blocking.connect() } returns connAck

        val config = mockk<Mqtt3ClientConfig>(relaxed = true)
        every { config.state } returns MqttClientState.DISCONNECTED

        val client = mockk<Mqtt3Client>(relaxed = true)
        every { client.config } returns config
        every { client.state } returns MqttClientState.DISCONNECTED
        every { client.toBlocking() } returns blocking
        return client
    }

    // AC7
    // GIVEN the broker answers the connect with a non-success CONNACK
    // WHEN the connect use case is invoked
    // THEN should emit a Left, not a Right
    test("testStory2_rejectedConnAckEmitsLeft") {
        val manager = HiveMqttClientManager(clientRespondingWith(Mqtt3ConnAckReturnCode.NOT_AUTHORIZED))
        val target = ConnectUseCase(ConnectMqttSource(manager))

        target(Unit).test {
            awaitItem().shouldBeInstanceOf<Either.Left<Throwable>>()
            awaitComplete()
        }
    }
})
