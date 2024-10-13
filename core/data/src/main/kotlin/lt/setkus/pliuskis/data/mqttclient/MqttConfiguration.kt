package lt.setkus.pliuskis.data.mqttclient

import javax.net.ssl.KeyManagerFactory

data class MqttConfiguration(
    val clientId: String,
    val brokerUrl: String,
    val port: Int,
    val keyManagerFactory: KeyManagerFactory?
)
