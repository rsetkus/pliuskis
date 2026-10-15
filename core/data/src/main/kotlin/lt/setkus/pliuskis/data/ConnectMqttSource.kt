package lt.setkus.pliuskis.data

import lt.setkus.pliuskis.core.connect.Connectable
import lt.setkus.pliuskis.data.mqttclient.MqttConnectionState

class ConnectMqttSource(
    private val manager: IotManager
) : Connectable {

    /** Throws when the broker rejects the connection, so callers see it as a failure. */
    override fun connect(): String {
        val state = manager.connect()
        check(state == MqttConnectionState.CONNECTED) { "Broker rejected the connection: $state" }
        return state.toString()
    }
}
