package lt.setkus.pliuskis.data

import kotlinx.coroutines.flow.Flow
import lt.setkus.pliuskis.data.mqttclient.MqttConnectionState
import lt.setkus.pliuskis.data.mqttclient.MqttMessageStatus

interface IotManager {
    fun connect(): Flow<MqttConnectionState>
    fun getClientId(): String
    fun publishString(message: String, topic: String)
}