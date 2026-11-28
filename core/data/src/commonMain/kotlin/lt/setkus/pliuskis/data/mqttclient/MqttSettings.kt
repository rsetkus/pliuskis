package lt.setkus.pliuskis.data.mqttclient

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Broker settings that the app module passes to the data layer. The type is platform neutral: the TLS key material
 * is not part of it, it comes from an [MqttTlsSetup] when [tlsMode] is [TlsMode.CLIENT_CERTIFICATE]. With
 * [TlsMode.WEBSOCKET_SIGV4] (AWS IoT over Cognito), [host] is the AWS IoT endpoint, [port] 443 and [clientId] the
 * install suffix of the client ID `<Cognito identity ID>-<install suffix>`; the signed-in identity's credentials and
 * the signed URL of every attempt come from the data layer.
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
) {
    /**
     * Where the client connects: `tcp://<host>:<port>` (plaintext), `ssl://<host>:<port>` (TLS with a client
     * certificate) or `wss://<host>:<port>/mqtt` (MQTT over a SigV4-signed WebSocket; the signed query of each attempt
     * is not part of it).
     */
    val connectionTarget: String
        get() = when (tlsMode) {
            TlsMode.NONE -> "tcp://$host:$port"
            TlsMode.CLIENT_CERTIFICATE -> "ssl://$host:$port"
            TlsMode.WEBSOCKET_SIGV4 -> "wss://$host:$port$WEBSOCKET_PATH"
        }
}

/** The path of AWS IoT's MQTT over WebSocket. */
internal const val WEBSOCKET_PATH = "/mqtt"

@OptIn(ExperimentalObjCName::class)
@ObjCName("TlsMode", exact = true)
enum class TlsMode {
    /** Plain TCP, for the local debug broker. */
    NONE,

    /** TLS with a client certificate, for production AWS IoT. */
    CLIENT_CERTIFICATE,

    /**
     * MQTT over WebSocket with TLS (`wss://<endpoint>:443/mqtt`) to AWS IoT, with a URL signed with SigV4 (service
     * `iotdevicegateway`) from the signed-in user's temporary Cognito credentials; a new URL on every connect attempt.
     */
    WEBSOCKET_SIGV4,
}
