package lt.setkus.pliuskis.data

import lt.setkus.pliuskis.core.connect.Connectable
import lt.setkus.pliuskis.data.mqttclient.MqttConnectionState

class ConnectMqttSource(
    private val manager: IotManager
) : Connectable {

    /** Throws when the connection isn't established (rejected, or a pending attempt timed out). */
    override fun connect(): String {
        val state = manager.connect()
        check(state == MqttConnectionState.CONNECTED) { "MQTT connection not established: $state" }
        return state.toString()
    }
}
