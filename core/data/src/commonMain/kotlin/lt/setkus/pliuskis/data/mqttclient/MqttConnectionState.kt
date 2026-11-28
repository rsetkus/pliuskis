package lt.setkus.pliuskis.data.mqttclient

enum class MqttConnectionState {
    CONNECTING,
    CONNECTED,
    FAILED,

    /**
     * The broker answered the attempt but refused the identity: on AWS IoT, the WebSocket upgrade was answered with
     * HTTP 403, or the connection was closed after the CONNECT and before any CONNACK (an identity without an AWS IoT
     * policy). Unlike [FAILED], it is not a network error.
     */
    REFUSED,
}
