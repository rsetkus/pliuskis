package lt.setkus.pliuskis.data.mqttclient

import platform.Foundation.NSData

/**
 * Stands in for the Swift `CocoaMqttTransport` over WebSocket (AWS IoT). Like the real one, every connect attempt, the
 * first one and each automatic reconnect, asks [webSocketUrl] for the URL to open; a failure there (no fresh
 * credentials) fails only that attempt. The test plays AWS IoT's side: accept the CONNECT (CONNACK), drop the
 * connection, refuse it (AWS IoT closes it before any CONNACK, or answers the upgrade with HTTP 403: an identity
 * without AWS IoT access), or fail with a socket error before any answer (no network, DNS failure).
 *
 * CocoaMQTT reconnects on its own after a lost connection until the Kotlin client stops it with [disconnect];
 * [reconnectOnItsOwn] plays that automatic reconnect and returns false when the client stopped the transport.
 */
class FakeWebSocketMqttTransport(private val webSocketUrl: () -> String) : MqttTransport {

    /** One connect attempt: the client ID it connects as, and the URL it opened (null when no URL could be had). */
    data class Attempt(val clientId: String, val url: String?)

    override var listener: MqttTransportListener? = null

    /** Every connect attempt, in order: the ones [connect] started and the automatic ones. */
    val attempts = mutableListOf<Attempt>()
    val subscribes = mutableListOf<String>()
    val publishes = mutableListOf<FakeMqttTransport.PublishCall>()

    /** True after the Kotlin client closed the transport: CocoaMQTT then makes no automatic reconnect. */
    var stopped = false
        private set

    private var clientId = ""
    private var lastMessageId = 0

    override fun connect(host: String, port: Int, clientId: String) {
        this.clientId = clientId
        stopped = false
        startAttempt()
    }

    override fun disconnect() {
        stopped = true
        listener?.onDisconnected()
    }

    override fun subscribe(topicFilter: String) {
        subscribes += topicFilter
    }

    override fun unsubscribe(topicFilter: String) = Unit

    override fun publish(topic: String, payload: NSData): Int {
        val messageId = ++lastMessageId
        publishes += FakeMqttTransport.PublishCall(topic, payload.toByteArray(), messageId)
        return messageId
    }

    /** CocoaMQTT's automatic reconnect after a lost connection; false when the client stopped the transport. */
    fun reconnectOnItsOwn(): Boolean {
        val reconnects = !stopped
        if (reconnects) startAttempt()
        return reconnects
    }

    /** AWS IoT accepted the CONNECT: CONNACK with return code 0. */
    fun acceptConnection() = requireListener().onConnected()

    /** The connection is lost after it was up (Wi-Fi off, AWS IoT's 24-hour limit). */
    fun dropConnection() = requireListener().onDisconnected()

    /** AWS IoT refused the identity: it closed the connection before any CONNACK (or answered the upgrade 403). */
    fun refuseConnection() = requireListener().onConnectionRefused()

    /** The attempt failed before any answer from AWS: no network, DNS failure. */
    fun failWithSocketError() = requireListener().onDisconnected()

    /** The broker answers PUBACK for the message published with [messageId]. */
    fun acknowledgePublish(messageId: Int) = requireListener().onPublishAcknowledged(messageId)

    private fun startAttempt() {
        requireListener().onConnecting()
        val url = runCatching(webSocketUrl).getOrNull()
        attempts += Attempt(clientId, url)
        if (url == null) requireListener().onDisconnected()
    }

    private fun requireListener(): MqttTransportListener =
        checkNotNull(listener) { "The client under test never set a listener on the transport" }
}
