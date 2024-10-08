package lt.setkus.pliuskis.data

import kotlinx.coroutines.flow.Flow
import lt.setkus.pliuskis.data.mqttclient.MqttConnectionState
import lt.setkus.pliuskis.data.mqttclient.MqttMessageStatus

interface IotManager {
    fun connect(): Flow<MqttConnectionState>
    fun publishString(message: String, topic: String): Flow<MqttMessageStatus>
}