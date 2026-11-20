package lt.setkus.pliuskis.data.wire

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import lt.setkus.pliuskis.data.IotManager
import lt.setkus.pliuskis.data.mqttclient.IotMessage
import lt.setkus.pliuskis.data.mqttclient.MqttConnectionState

data class PublishedMessage(val topic: String, val message: String)

/**
 * Fake [IotManager] that records what is published, which topics are subscribed and how often the client id is read.
 * A subscription to a topic filter emits the messages given for it in [messagesByTopicFilter] (none by default).
 */
class RecordingIotManager(
    private val clientId: String,
    private val messagesByTopicFilter: Map<String, List<IotMessage>> = emptyMap(),
) : IotManager {
    val published = mutableListOf<PublishedMessage>()
    val subscribed = mutableListOf<String>()
    var clientIdReads = 0
        private set

    override suspend fun connect(): MqttConnectionState = error("connect is not used by the tests of this fake")

    override fun getClientId(): String {
        clientIdReads += 1
        return clientId
    }

    override suspend fun publishString(message: String, topic: String) {
        published += PublishedMessage(topic, message)
    }

    override fun subscribe(topic: String): Flow<IotMessage> {
        subscribed += topic
        return messagesByTopicFilter[topic].orEmpty().asFlow()
    }

    /** These tests never reconnect. */
    override fun reconnects(): Flow<Unit> = emptyFlow()
}
