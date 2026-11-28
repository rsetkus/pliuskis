package lt.setkus.pliuskis.data.mqttclient

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttClientSslConfig
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client

/**
 * Builds the HiveMQ client for [settings]. [tlsSetup] is required for [TlsMode.CLIENT_CERTIFICATE] and ignored for
 * [TlsMode.NONE]. [TlsMode.WEBSOCKET_SIGV4] has no single client: [AwsIotMqttClientManager] builds one per
 * identity. Building the client does not connect. The client reports its reconnects through a
 * [ReconnectListener] in its configuration ([HiveMqttClientManager.reconnects]).
 */
fun createMqttClient(settings: MqttSettings, tlsSetup: MqttTlsSetup?): Mqtt3Client {
    val builder = MqttClient.builder()
        .identifier(settings.clientId)
        .serverHost(settings.host)
        .serverPort(settings.port)
        .automaticReconnectWithDefaultConfig()
        .addConnectedListener(ReconnectListener())
    val secureBuilder = when (settings.tlsMode) {
        TlsMode.NONE -> builder
        TlsMode.CLIENT_CERTIFICATE -> {
            val setup = requireNotNull(tlsSetup) { "TlsMode.CLIENT_CERTIFICATE needs an MqttTlsSetup in Koin" }
            builder.sslConfig(sslConfig(setup))
        }
        TlsMode.WEBSOCKET_SIGV4 ->
            throw IllegalArgumentException("TlsMode.WEBSOCKET_SIGV4 is connected by AwsIotMqttClientManager")
    }
    return secureBuilder.useMqttVersion3().build()
}

private fun sslConfig(tlsSetup: MqttTlsSetup): MqttClientSslConfig =
    MqttClientSslConfig.builder()
        .keyManagerFactory(tlsSetup.keyManagerFactory())
        .build()
