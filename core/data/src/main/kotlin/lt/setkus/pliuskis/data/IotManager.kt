package lt.setkus.pliuskis.data

import kotlinx.coroutines.flow.Flow
import lt.setkus.pliuskis.data.mqttclient.MqttConnectionState

interface IotManager {
    fun connect(): MqttConnectionState
    fun getClientId(): String
    fun publishString(message: String = "", topic: String)
    fun subscribe(topic: String): Flow<String>
}