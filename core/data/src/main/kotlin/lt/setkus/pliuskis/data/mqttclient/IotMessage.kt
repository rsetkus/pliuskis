package lt.setkus.pliuskis.data.mqttclient

data class IotMessage(
    val topic: String,
    val payload: String
)
