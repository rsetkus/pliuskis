package lt.setkus.pliuskis.data

import kotlinx.coroutines.flow.Flow
import lt.setkus.pliuskis.core.connect.Connectable
import lt.setkus.pliuskis.core.connect.Reconnectable
import lt.setkus.pliuskis.data.mqttclient.MqttConnectionState

class ConnectMqttSource(
    private val manager: IotManager
) : Connectable, Reconnectable {

    /** Throws when the connection isn't established (rejected, or a pending attempt timed out). */
    override suspend fun connect(): String {
        val state = manager.connect()
        check(state == MqttConnectionState.CONNECTED) { "MQTT connection not established: $state" }
        return state.toString()
    }

    override fun reconnects(): Flow<Unit> = manager.reconnects()
}
