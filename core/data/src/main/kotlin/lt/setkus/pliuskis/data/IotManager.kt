package lt.setkus.pliuskis.data

import lt.setkus.pliuskis.data.mqttclient.MqttConnectionState

interface IotManager {
    fun connect(): MqttConnectionState
    fun getClientId(): String
    fun publishString(message: String, topic: String)
}