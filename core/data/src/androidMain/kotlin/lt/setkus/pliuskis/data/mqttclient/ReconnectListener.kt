package lt.setkus.pliuskis.data.mqttclient

import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedContext
import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedListener
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean

private const val RECONNECT_BUFFER = 8

/**
 * HiveMQ calls this listener on every CONNACK, the first connection's and each automatic reconnect's.
 * [createMqttClient] adds it to the client's configuration, and [HiveMqttClientManager.reconnects] reads [reconnects]
 * from there. The first CONNACK is the client's first connection, not a reconnect; every later one follows a lost
 * connection. Nothing is replayed: a collector gets only the reconnects after it started.
 */
internal class ReconnectListener : MqttClientConnectedListener {

    private val connectedBefore = AtomicBoolean(false)
    private val events = MutableSharedFlow<Unit>(
        extraBufferCapacity = RECONNECT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val reconnects: Flow<Unit> = events.asSharedFlow()

    override fun onConnected(context: MqttClientConnectedContext) {
        if (connectedBefore.getAndSet(true)) {
            events.tryEmit(Unit)
        }
    }
}
