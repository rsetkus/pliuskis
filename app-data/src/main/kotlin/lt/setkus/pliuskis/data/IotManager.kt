package lt.setkus.pliuskis.data

import kotlinx.coroutines.flow.Flow
import lt.setkus.pliuskis.data.aws.MqttConnectionState
import lt.setkus.pliuskis.data.aws.MqttMessageStatus

interface IotManager {
    fun connect(): Flow<MqttConnectionState>
    fun publishString(message: String, topic: String): Flow<MqttMessageStatus>
}