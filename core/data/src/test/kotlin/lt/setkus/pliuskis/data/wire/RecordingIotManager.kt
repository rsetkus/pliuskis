package lt.setkus.pliuskis.data.wire

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import lt.setkus.pliuskis.data.IotManager
import lt.setkus.pliuskis.data.mqttclient.IotMessage
import lt.setkus.pliuskis.data.mqttclient.MqttConnectionState

data class PublishedMessage(val topic: String, val message: String)

/** Fake [IotManager] that records what is published and which topics are subscribed. */
class RecordingIotManager(private val clientId: String) : IotManager {
    val published = mutableListOf<PublishedMessage>()
    val subscribed = mutableListOf<String>()

    override fun connect(): MqttConnectionState = error("connect is not used by the wire format tests")

    override fun getClientId(): String = clientId

    override fun publishString(message: String, topic: String) {
        published += PublishedMessage(topic, message)
    }

    override fun subscribe(topic: String): Flow<IotMessage> {
        subscribed += topic
        return emptyFlow()
    }
}
