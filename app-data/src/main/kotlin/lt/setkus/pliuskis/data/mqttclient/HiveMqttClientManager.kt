package lt.setkus.pliuskis.data.mqttclient

import com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAckReturnCode
import lt.setkus.pliuskis.data.IotManager
import java.nio.charset.Charset
import kotlin.jvm.optionals.getOrNull

class HiveMqttClientManager(private val client: Mqtt3Client) : IotManager {

    private fun mapMqtt3ConnAckCode(conn: Mqtt3ConnAck): MqttConnectionState {
        return when (conn.returnCode) {
            Mqtt3ConnAckReturnCode.SUCCESS -> MqttConnectionState.CONNECTED
            else -> MqttConnectionState.FAILED
        }
    }

    override fun connect() = mapMqtt3ConnAckCode(client.toBlocking().connect())

    override fun getClientId() =
        client.config.clientIdentifier.getOrNull()?.toString() ?: "default-client-id"

    override fun publishString(message: String, topic: String) {
        client.toBlocking()
            .publishWith()
            .topic(topic)
            .payload(message.toByteArray(Charset.defaultCharset()))
            .qos(AT_LEAST_ONCE)
            .send()
    }
}