package lt.setkus.pliuskis.data

import kotlinx.coroutines.flow.Flow
import lt.setkus.pliuskis.data.aws.MqttConnectionState

interface IotManager {
    fun connect(): Flow<MqttConnectionState>
}