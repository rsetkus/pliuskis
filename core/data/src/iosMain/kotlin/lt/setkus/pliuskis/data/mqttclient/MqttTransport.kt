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
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MqttTransport", exact = true)
interface MqttTransport {
    /** Receives the connection events and messages. The Kotlin client sets it once, when it is created. */
    var listener: MqttTransportListener?

    /** Opens the connection: CONNECT with a clean session. Reports [MqttTransportListener.onConnecting] first. */
    fun connect(host: String, port: Int, clientId: String)

    /** Sends SUBSCRIBE for [topicFilter] with QoS 1. */
    fun subscribe(topicFilter: String)

    /** Sends UNSUBSCRIBE for [topicFilter]. */
    fun unsubscribe(topicFilter: String)

    /** Sends PUBLISH of [payload] on [topic] with QoS 1, not retained. */
    fun publish(topic: String, payload: NSData)
}

/** What the [MqttTransport] reports to Kotlin. CocoaMQTT calls it on the main queue. */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MqttTransportListener", exact = true)
interface MqttTransportListener {
    /** A connect attempt started: one asked for by [MqttTransport.connect], or an automatic reconnect. */
    fun onConnecting()

    /** The broker accepted the connection (CONNACK with return code 0). */
    fun onConnected()

    /** The connection is gone or could not be opened: refused CONNACK, socket error, network loss, broker restart. */
    fun onDisconnected()

    /** A PUBLISH arrived on [topic]; [payload] holds its raw bytes. */
    fun onMessage(topic: String, payload: NSData)
}
