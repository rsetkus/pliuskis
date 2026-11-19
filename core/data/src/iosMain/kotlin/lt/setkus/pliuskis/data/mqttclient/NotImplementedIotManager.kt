package lt.setkus.pliuskis.data.mqttclient

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import lt.setkus.pliuskis.data.IotManager

/**
 * Stands in for the iOS MQTT client, which is not part of this module yet: [connect] always returns
 * [MqttConnectionState.FAILED], [publishString] sends nothing and [subscribe] emits nothing. Screens on iOS therefore
 * end in their error state. The real client replaces this binding in `iosDataModule`.
 */
internal class NotImplementedIotManager(private val settings: MqttSettings) : IotManager {
    override suspend fun connect(): MqttConnectionState = MqttConnectionState.FAILED

    override fun getClientId(): String = settings.clientId

    override suspend fun publishString(message: String, topic: String) = Unit

    override fun subscribe(topic: String): Flow<IotMessage> = emptyFlow()
}
