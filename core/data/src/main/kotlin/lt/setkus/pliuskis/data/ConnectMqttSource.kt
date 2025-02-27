package lt.setkus.pliuskis.data

import lt.setkus.pliuskis.core.connect.Connectable

class ConnectMqttSource(
    private val manager: IotManager
): Connectable {
    override fun connect() = manager.connect().toString()
}