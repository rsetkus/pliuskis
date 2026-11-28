package lt.setkus.pliuskis.data

import kotlinx.coroutines.flow.Flow
import lt.setkus.pliuskis.core.connect.Connectable
import lt.setkus.pliuskis.core.connect.IotAccessDeniedException
import lt.setkus.pliuskis.core.connect.Reconnectable
import lt.setkus.pliuskis.data.mqttclient.MqttConnectionState

class ConnectMqttSource(
    private val manager: IotManager
) : Connectable, Reconnectable {

    /**
     * Throws when the connection isn't established: [IotAccessDeniedException] when the broker refused the signed-in
     * identity (no AWS IoT access), an [IllegalStateException] otherwise (rejected, network error, or a running attempt
     * that timed out).
     */
    override suspend fun connect(): String {
        val state = manager.connect()
        if (state == MqttConnectionState.REFUSED) throw IotAccessDeniedException()
        check(state == MqttConnectionState.CONNECTED) { "MQTT connection not established: $state" }
        return state.toString()
    }

    override fun reconnects(): Flow<Unit> = manager.reconnects()
}
