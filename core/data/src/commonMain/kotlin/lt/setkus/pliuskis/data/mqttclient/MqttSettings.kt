package lt.setkus.pliuskis.data.mqttclient

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Broker settings that the app module passes to the data layer. The type is platform neutral: the TLS key material
 * is not part of it, it comes from an [MqttTlsSetup] when [tlsMode] is [TlsMode.CLIENT_CERTIFICATE].
 *
 * Swift passes it to `initShared` of the `shared` framework, under the exact name `MqttSettings`.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MqttSettings", exact = true)
data class MqttSettings(
    val clientId: String,
    val host: String,
    val port: Int,
    val tlsMode: TlsMode,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("TlsMode", exact = true)
enum class TlsMode {
    /** Plain TCP, for the local debug broker. */
    NONE,

    /** TLS with a client certificate, for production AWS IoT. */
    CLIENT_CERTIFICATE,
}
