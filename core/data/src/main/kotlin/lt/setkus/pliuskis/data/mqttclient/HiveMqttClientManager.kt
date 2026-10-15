package lt.setkus.pliuskis.data.mqttclient

import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE
import com.hivemq.client.mqtt.exceptions.MqttClientStateException
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAckReturnCode
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import lt.setkus.pliuskis.data.IotManager
import timber.log.Timber
import java.nio.charset.Charset
import kotlin.jvm.optionals.getOrNull

private const val PENDING_CONNECTION_TIMEOUT_MILLIS = 10_000L
private const val PENDING_CONNECTION_POLL_MILLIS = 100L

class HiveMqttClientManager(
    private val client: Mqtt3Client,
    private val pendingConnectionTimeoutMillis: Long = PENDING_CONNECTION_TIMEOUT_MILLIS,
    private val pendingConnectionPollMillis: Long = PENDING_CONNECTION_POLL_MILLIS,
) : IotManager {

    private fun mapMqtt3ConnAckCode(conn: Mqtt3ConnAck): MqttConnectionState {
        return when (conn.returnCode) {
            Mqtt3ConnAckReturnCode.SUCCESS -> MqttConnectionState.CONNECTED
            else -> MqttConnectionState.FAILED
        }
    }

    /**
     * Connects a disconnected client. A client that is already connecting or reconnecting is not
     * connected yet: wait for that attempt (HiveMQ throws [MqttClientStateException] on a second
     * connect) and report [MqttConnectionState.FAILED] if it doesn't succeed in time.
     */
    override fun connect(): MqttConnectionState {
        val state = when (client.state) {
            MqttClientState.DISCONNECTED -> connectBlocking()
            MqttClientState.CONNECTED -> MqttConnectionState.CONNECTED
            else -> awaitPendingConnection()
        }
        return state
    }

    private fun connectBlocking(): MqttConnectionState {
        val state = try {
            mapMqtt3ConnAckCode(client.toBlocking().connect())
        } catch (alreadyConnecting: MqttClientStateException) {
            // Another caller started connecting between the state check and this call.
            Timber.d(alreadyConnecting, "MQTT client is already connecting")
            awaitPendingConnection()
        }
        return state
    }

    /** Waits for an attempt someone else started (a first connect or HiveMQ's automatic reconnect). */
    private fun awaitPendingConnection(): MqttConnectionState {
        val deadline = System.currentTimeMillis() + pendingConnectionTimeoutMillis
        while (!client.state.isConnected && System.currentTimeMillis() < deadline) {
            Thread.sleep(pendingConnectionPollMillis)
        }
        return if (client.state.isConnected) MqttConnectionState.CONNECTED else MqttConnectionState.FAILED
    }

    override fun getClientId() =
        client.config.clientIdentifier.getOrNull()?.toString() ?: "default-client-id"

    override fun publishString(message: String, topic: String) {
        client.toBlocking()
            .publishWith()
            .topic(topic)
            .payload(message.toByteArray(Charset.defaultCharset()))
            .qos(AT_LEAST_ONCE)
            .send()
    }

    override fun subscribe(topic: String): Flow<IotMessage> = callbackFlow {
        val listener: (Mqtt3Publish) -> Unit = { result: Mqtt3Publish ->
            val payload = result.payloadAsBytes.toString(Charset.defaultCharset())
            trySend(IotMessage(result.topic.toString(), payload))
        }

        client
            .toAsync()
            .subscribeWith()
            .topicFilter(topic)
            .callback(listener)
            .send()

        awaitClose {
            client
                .toAsync()
                .unsubscribeWith()
                .topicFilter(topic)
                .send()
        }
    }
}
