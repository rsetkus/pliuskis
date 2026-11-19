package lt.setkus.pliuskis.data

import kotlinx.coroutines.flow.Flow
import lt.setkus.pliuskis.data.mqttclient.IotMessage
import lt.setkus.pliuskis.data.mqttclient.MqttConnectionState

/**
 * The app's single MQTT client. Every platform implementation follows the same contract:
 *
 * - [connect] and [publishString] suspend and never block the calling thread, so they are safe to call from the main
 *   thread.
 * - [connect] is safe to call at any time. A client that is already connected returns
 *   [MqttConnectionState.CONNECTED] at once. While a connect attempt is in progress (another caller's, or the
 *   client's automatic reconnect), connecting does not start a second attempt: it waits for that attempt and returns
 *   its result, or [MqttConnectionState.FAILED] if the attempt does not connect within 10 s.
 * - A subscription made with [subscribe] survives a reconnect: the client re-subscribes on its own, and the flow keeps
 *   emitting without being collected again.
 */
interface IotManager {
    suspend fun connect(): MqttConnectionState
    fun getClientId(): String
    suspend fun publishString(message: String = "", topic: String)
    fun subscribe(topic: String): Flow<IotMessage>
}
