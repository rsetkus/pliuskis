package lt.setkus.pliuskis.data.mqttclient

import co.touchlab.kermit.Logger
import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE
import com.hivemq.client.mqtt.exceptions.MqttClientStateException
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAckReturnCode
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import lt.setkus.pliuskis.data.IotManager
import java.nio.charset.Charset
import kotlin.jvm.optionals.getOrNull

private const val PENDING_CONNECTION_TIMEOUT_MILLIS = 10_000L
private const val PENDING_CONNECTION_POLL_MILLIS = 100L

class HiveMqttClientManager(
    private val client: Mqtt3Client,
    private val pendingConnectionTimeoutMillis: Long = PENDING_CONNECTION_TIMEOUT_MILLIS,
    private val pendingConnectionPollMillis: Long = PENDING_CONNECTION_POLL_MILLIS,
) : IotManager {

    private val log = Logger.withTag("HiveMqttClientManager")

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
    override suspend fun connect(): MqttConnectionState {
        val state = when (client.state) {
            MqttClientState.DISCONNECTED -> connectDisconnected()
            MqttClientState.CONNECTED -> MqttConnectionState.CONNECTED
            else -> awaitPendingConnection()
        }
        return state
    }

    /** HiveMQ's connect call blocks until the CONNACK arrives, so it runs on the I/O dispatcher. */
    private suspend fun connectDisconnected(): MqttConnectionState {
        val state = try {
            withContext(Dispatchers.IO) { mapMqtt3ConnAckCode(client.toBlocking().connect()) }
        } catch (alreadyConnecting: MqttClientStateException) {
            // Another caller started connecting between the state check and this call.
            log.d("MQTT client is already connecting", alreadyConnecting)
            awaitPendingConnection()
        }
        return state
    }

    /**
     * Waits for an attempt someone else started (a first connect or HiveMQ's automatic reconnect). It only reads the
     * client state, suspending between reads, so the caller's thread is never blocked.
     */
    private suspend fun awaitPendingConnection(): MqttConnectionState {
        val connected = withTimeoutOrNull(pendingConnectionTimeoutMillis) {
            while (!client.state.isConnected) {
                delay(pendingConnectionPollMillis)
            }
        }
        return if (connected != null) MqttConnectionState.CONNECTED else MqttConnectionState.FAILED
    }

    /** The client is always built with an identifier (the per-install ID), so it is never absent. */
    override fun getClientId(): String {
        val clientId = client.config.clientIdentifier.getOrNull()
        return checkNotNull(clientId) { "The MQTT client was built without a client identifier" }.toString()
    }

    override suspend fun publishString(message: String, topic: String) {
        client.toAsync()
            .publishWith()
            .topic(topic)
            .payload(message.toByteArray(Charset.defaultCharset()))
            .qos(AT_LEAST_ONCE)
            .send()
            .await()
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
