package lt.setkus.pliuskis.data.mqttclient

import app.cash.turbine.test
import arrow.core.Either
import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.exceptions.MqttClientStateException
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import lt.setkus.pliuskis.core.connect.ConnectUseCase
import lt.setkus.pliuskis.data.ConnectMqttSource

private const val SHORT_TIMEOUT_MILLIS = 200L
private const val POLL_MILLIS = 10L

/**
 * A client that is connecting or reconnecting isn't connected yet. connect() must wait for that
 * attempt instead of reporting success straight away, and must not start a second connect.
 */
class HiveMqttClientManagerPendingConnectionTest : FunSpec({

    fun clientWithStates(vararg states: MqttClientState): Pair<Mqtt3Client, Mqtt3BlockingClient> {
        val blocking = mockk<Mqtt3BlockingClient>(relaxed = true)
        val client = mockk<Mqtt3Client>(relaxed = true)
        every { client.state } returnsMany states.toList()
        every { client.toBlocking() } returns blocking
        return client to blocking
    }

    fun manager(client: Mqtt3Client) = HiveMqttClientManager(
        client,
        pendingConnectionTimeoutMillis = SHORT_TIMEOUT_MILLIS,
        pendingConnectionPollMillis = POLL_MILLIS
    )

    test("GIVEN a reconnect in progress that completes WHEN connecting THEN reports connected, no second connect") {
        val (client, blocking) = clientWithStates(
            MqttClientState.DISCONNECTED_RECONNECT,
            MqttClientState.CONNECTING_RECONNECT,
            MqttClientState.CONNECTED
        )

        manager(client).connect() shouldBe MqttConnectionState.CONNECTED
        verify(exactly = 0) { blocking.connect() }
    }

    test("GIVEN a reconnect that never completes WHEN connecting THEN the connect use case emits a Left") {
        val (client, blocking) = clientWithStates(MqttClientState.DISCONNECTED_RECONNECT)

        ConnectUseCase(ConnectMqttSource(manager(client)))(Unit).test {
            awaitItem().shouldBeInstanceOf<Either.Left<Throwable>>()
            awaitComplete()
        }
        verify(exactly = 0) { blocking.connect() }
    }

    test("GIVEN another caller starts connecting first WHEN connecting THEN waits for that attempt") {
        val (client, blocking) = clientWithStates(
            MqttClientState.DISCONNECTED,
            MqttClientState.CONNECTING,
            MqttClientState.CONNECTED
        )
        every { blocking.connect() } throws MqttClientStateException("MQTT client is already connecting.")

        manager(client).connect() shouldBe MqttConnectionState.CONNECTED
    }

    test("GIVEN that concurrent attempt fails WHEN connecting THEN reports failed") {
        val (client, blocking) = clientWithStates(MqttClientState.DISCONNECTED, MqttClientState.CONNECTING)
        every { blocking.connect() } throws MqttClientStateException("MQTT client is already connecting.")

        manager(client).connect() shouldBe MqttConnectionState.FAILED
    }
})
