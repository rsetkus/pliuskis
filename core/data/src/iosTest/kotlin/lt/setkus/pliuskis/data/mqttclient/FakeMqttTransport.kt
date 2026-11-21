package lt.setkus.pliuskis.data.mqttclient

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

/**
 * Stands in for the Swift CocoaMQTT transport. It records every call the Kotlin client makes (the packets it would
 * send to the broker), and lets a test play the broker's side: accept a connection (CONNACK), drop it, start an
 * automatic reconnect, deliver a PUBLISH, and acknowledge a published message (PUBACK). Each publish gets the next
 * packet identifier, starting at 1, unless [refusePublishes] makes it return -1 as CocoaMQTT does when it refuses one.
 */
class FakeMqttTransport : MqttTransport {

    data class ConnectCall(val host: String, val port: Int, val clientId: String)

    class PublishCall(val topic: String, val payload: ByteArray, val messageId: Int)

    override var listener: MqttTransportListener? = null

    val connectCalls = mutableListOf<ConnectCall>()
    val subscribes = mutableListOf<String>()
    val unsubscribes = mutableListOf<String>()
    val publishes = mutableListOf<PublishCall>()
    var refusePublishes = false
    private var lastMessageId = 0

    override fun connect(host: String, port: Int, clientId: String) {
        connectCalls += ConnectCall(host, port, clientId)
    }

    override fun subscribe(topicFilter: String) {
        subscribes += topicFilter
    }

    override fun unsubscribe(topicFilter: String) {
        unsubscribes += topicFilter
    }

    override fun publish(topic: String, payload: NSData): Int {
        val messageId = if (refusePublishes) -1 else ++lastMessageId
        publishes += PublishCall(topic, payload.toByteArray(), messageId)
        return messageId
    }

    /** The broker accepted the connection: a CONNACK with return code 0. */
    fun acceptConnection() = requireListener().onConnected()

    /** The connection is gone (refused, socket error, Wi-Fi off, broker restart). */
    fun dropConnection() = requireListener().onDisconnected()

    /** The transport started an automatic reconnect attempt on its own. */
    fun startAutomaticReconnect() = requireListener().onConnecting()

    /** The broker answers PUBACK for the message published with [messageId]. */
    fun acknowledgePublish(messageId: Int) = requireListener().onPublishAcknowledged(messageId)

    /** The broker delivers a PUBLISH whose payload is exactly [payload]. */
    fun deliver(topic: String, payload: ByteArray) = requireListener().onMessage(topic, payload.toNSData())

    private fun requireListener(): MqttTransportListener =
        checkNotNull(listener) { "The client under test never set a listener on the transport" }
}

@OptIn(ExperimentalForeignApi::class)
fun ByteArray.toNSData(): NSData {
    val data = if (isEmpty()) {
        NSData()
    } else {
        usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
    }
    return data
}

@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray {
    val bytes = ByteArray(length.toInt())
    if (bytes.isNotEmpty()) {
        bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    }
    return bytes
}
