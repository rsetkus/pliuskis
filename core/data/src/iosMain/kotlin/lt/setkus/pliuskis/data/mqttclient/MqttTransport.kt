package lt.setkus.pliuskis.data.mqttclient

import platform.Foundation.NSData
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * The MQTT socket on iOS. The app implements it in Swift over CocoaMQTT (`CocoaMqttTransport`, MQTT 3.1.1, clean
 * session, automatic reconnect) and passes it to `initShared`. It only moves packets; the MQTT client contract
 * (connect de-duplication, the 10 s wait, re-subscribing after a CONNACK, topic filter matching, UTF-8) is Kotlin's.
 *
 * Every call returns at once and reports the outcome later through [listener].
 *
 * Over AWS IoT (`CocoaMqttTransport(webSocketUrlProvider:)`), every attempt, CocoaMQTT's automatic reconnects
 * included, opens a newly signed WebSocket URL from the shared `signedAwsIotWebSocketUrl()`.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MqttTransport", exact = true)
interface MqttTransport {
    /** Receives the connection events and messages. The Kotlin client sets it once, when it is created. */
    var listener: MqttTransportListener?

    /**
     * Opens the connection: CONNECT with a clean session. Reports [MqttTransportListener.onConnecting] first. Turns
     * the automatic reconnect on again after a [disconnect].
     */
    fun connect(host: String, port: Int, clientId: String)

    /**
     * Closes the connection (or stops the attempt) and turns the automatic reconnect off until the next [connect]:
     * after AWS IoT refused the identity, or after a sign-out. Reports [MqttTransportListener.onDisconnected].
     */
    fun disconnect()

    /** Sends SUBSCRIBE for [topicFilter] with QoS 1. */
    fun subscribe(topicFilter: String)

    /** Sends UNSUBSCRIBE for [topicFilter]. */
    fun unsubscribe(topicFilter: String)

    /**
     * Queues PUBLISH of [payload] on [topic] with QoS 1, not retained. Returns the packet identifier, which
     * [MqttTransportListener.onPublishAcknowledged] reports when the broker answers with PUBACK, or -1 when the
     * transport refused the message (no connection object, invalid topic, no free packet identifier). A queued message
     * may still never reach the broker: CocoaMQTT also queues while disconnected and drops the queue on a clean-session
     * reconnect, so only the PUBACK says the broker took it.
     */
    fun publish(topic: String, payload: NSData): Int
}

/** What the [MqttTransport] reports to Kotlin. CocoaMQTT calls it on the main queue. */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MqttTransportListener", exact = true)
interface MqttTransportListener {
    /** A connect attempt started: one asked for by [MqttTransport.connect], or an automatic reconnect. */
    fun onConnecting()

    /** The broker accepted the connection (CONNACK with return code 0). */
    fun onConnected()

    /** The connection is gone or could not be opened: socket error, network loss, broker restart. */
    fun onDisconnected()

    /**
     * The broker answered the attempt but refused it, before any accepted CONNACK: the WebSocket upgrade answered with
     * an HTTP error (AWS IoT: 403), the connection closed after it opened and before the CONNACK (AWS IoT: an identity
     * without an AWS IoT policy), or a CONNACK with a refusal code. Reported instead of [onDisconnected].
     */
    fun onConnectionRefused()

    /** A PUBLISH arrived on [topic]; [payload] holds its raw bytes. */
    fun onMessage(topic: String, payload: NSData)

    /** The broker answered PUBACK for the message that [MqttTransport.publish] returned [messageId] for. */
    fun onPublishAcknowledged(messageId: Int)
}
