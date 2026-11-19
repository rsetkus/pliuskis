package lt.setkus.pliuskis.data.mqttclient

/**
 * Broker settings that the app module passes to the data layer. The type is platform neutral: the TLS key material
 * is not part of it, it comes from an [MqttTlsSetup] when [tlsMode] is [TlsMode.CLIENT_CERTIFICATE].
 */
data class MqttSettings(
    val clientId: String,
    val host: String,
    val port: Int,
    val tlsMode: TlsMode,
)

enum class TlsMode {
    /** Plain TCP, for the local debug broker. */
    NONE,

    /** TLS with a client certificate, for production AWS IoT. */
    CLIENT_CERTIFICATE,
}
