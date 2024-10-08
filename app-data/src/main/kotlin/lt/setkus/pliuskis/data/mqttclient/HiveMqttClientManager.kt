package lt.setkus.pliuskis.data.mqttclient

import com.hivemq.client.mqtt.mqtt3.Mqtt3RxClient
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAckReturnCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx2.asFlow
import lt.setkus.pliuskis.data.IotManager

class HiveMqttClientManager(private val client: Mqtt3RxClient) : IotManager {

    private fun mapMqtt3ConnAckCode(conn: Mqtt3ConnAck): MqttConnectionState {
        return when (conn.returnCode) {
            Mqtt3ConnAckReturnCode.SUCCESS -> MqttConnectionState.CONNECTED
            else -> MqttConnectionState.FAILED
        }
    }

    override fun connect(): Flow<MqttConnectionState> {
        return client
            .connect()
            .toObservable()
            .map(::mapMqtt3ConnAckCode)
            .asFlow()
    }

    override fun publishString(message: String, topic: String) = connect().map {
        if (it == MqttConnectionState.CONNECTED) {
            MqttMessageStatus.DELIVERED
        } else {
            MqttMessageStatus.FAILED
        }
    }
}