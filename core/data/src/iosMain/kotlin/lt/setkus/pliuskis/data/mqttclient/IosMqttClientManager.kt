package lt.setkus.pliuskis.data.mqttclient

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeoutOrNull
import lt.setkus.pliuskis.data.IotManager
import lt.setkus.pliuskis.data.util.locked
import platform.Foundation.NSData
import platform.Foundation.NSRecursiveLock

/** How long [IosMqttClientManager.connect] waits for a running connect attempt. */
internal const val PENDING_CONNECTION_TIMEOUT_MILLIS = 10_000L

/** How long [IosMqttClientManager.publishString] waits for the broker's PUBACK. */
internal const val PUBLISH_ACK_TIMEOUT_MILLIS = 10_000L

/** The broker did not take a message: not connected, refused by the transport, connection lost, or no PUBACK. */
internal class MqttPublishException(message: String) : Exception(message)

private const val RECONNECT_BUFFER = 8

/**
 * The iOS [IotManager]: the contract logic over the Swift CocoaMQTT socket [transport].
 *
 * - **Connect.** One attempt at a time. [connect] on a connected client returns at once; while an attempt runs (a
 *   caller's or the transport's automatic reconnect) it waits for that attempt's result, up to
 *   [pendingConnectionTimeoutMillis], and returns [MqttConnectionState.FAILED] if it does not connect in time.
 * - **Subscriptions survive a reconnect.** CocoaMQTT does not restore subscriptions on a clean session, so after every
 *   CONNACK this client sends SUBSCRIBE again for every open [subscribe] flow, and the flows keep emitting.
 * - **Unsubscribe on cancel.** When a flow's collector goes away, the transport gets one UNSUBSCRIBE for its filter
 *   (none while another open flow still uses the same filter).
 * - **Reconnects.** [reconnects] emits once for every CONNACK after the first one (each follows a lost connection),
 *   after the subscriptions are sent again. Nothing is replayed.
 * - **Messages.** The transport reports each PUBLISH once; this client matches its topic against the open filters
 *   (`+`, `#`) and decodes the payload as UTF-8.
 * - **Publish waits for PUBACK.** [publishString] returns when the broker acknowledges the message, as HiveMQ's
 *   QoS 1 publish does on Android. It throws [MqttPublishException] at once when not connected or when the transport
 *   refuses the message, and later when the connection drops or no PUBACK comes within [publishAckTimeoutMillis].
 *
 * The broker settings come from [settings]; iOS connects in plaintext (`TlsMode.NONE`), so the TLS mode is not used.
 * The transport calls back on the main queue and the callers may be on any thread, so the state is guarded by a lock.
 */
internal class IosMqttClientManager(
    private val transport: MqttTransport,
    private val settings: MqttSettings,
    private val pendingConnectionTimeoutMillis: Long = PENDING_CONNECTION_TIMEOUT_MILLIS,
    private val publishAckTimeoutMillis: Long = PUBLISH_ACK_TIMEOUT_MILLIS,
) : IotManager {

    private enum class Link { DISCONNECTED, CONNECTING, CONNECTED }

    private class Subscription(val filter: String, val messages: SendChannel<IotMessage>)

    private val lock = NSRecursiveLock()
    private var link = Link.DISCONNECTED
    private var runningAttempt: CompletableDeferred<MqttConnectionState>? = null
    private val subscriptions = mutableListOf<Subscription>()
    private val pendingAcks = mutableMapOf<Int, CompletableDeferred<Unit>>()
    private var connectedBefore = false
    private val reconnectEvents = MutableSharedFlow<Unit>(
        extraBufferCapacity = RECONNECT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        transport.listener = TransportEvents()
    }

    override suspend fun connect(): MqttConnectionState {
        val attempt = lock.locked { if (link == Link.CONNECTED) null else runningAttempt ?: startAttempt() }
        return if (attempt == null) {
            MqttConnectionState.CONNECTED
        } else {
            withTimeoutOrNull(pendingConnectionTimeoutMillis) { attempt.await() } ?: MqttConnectionState.FAILED
        }
    }

    override fun getClientId(): String = settings.clientId

    override suspend fun publishString(message: String, topic: String) {
        val ack = lock.locked { queuePublish(topic, message) }
        val acknowledged = withTimeoutOrNull(publishAckTimeoutMillis) { ack.await() } != null
        if (!acknowledged) {
            lock.locked { pendingAcks.values.remove(ack) }
            throw MqttPublishException("No PUBACK for $topic within $publishAckTimeoutMillis ms")
        }
    }

    override fun subscribe(topic: String): Flow<IotMessage> = callbackFlow {
        val subscription = Subscription(topic, channel)
        lock.locked {
            subscriptions += subscription
            if (link == Link.CONNECTED) transport.subscribe(topic)
        }
        awaitClose { release(subscription) }
    }.buffer(Channel.UNLIMITED)

    override fun reconnects(): Flow<Unit> = reconnectEvents.asSharedFlow()

    /** Must hold [lock]. Hands the message to the transport and returns what completes on its PUBACK. */
    private fun queuePublish(topic: String, message: String): CompletableDeferred<Unit> {
        if (link != Link.CONNECTED) throw MqttPublishException("Not connected, so $topic was not sent")
        val messageId = transport.publish(topic, message.toUtf8NSData())
        if (messageId < 0) throw MqttPublishException("The transport refused the message on $topic")
        val ack = CompletableDeferred<Unit>()
        pendingAcks[messageId] = ack
        return ack
    }

    /** Must hold [lock]. The listener may answer synchronously, so the attempt exists before the transport call. */
    private fun startAttempt(): CompletableDeferred<MqttConnectionState> {
        val attempt = attemptInProgress()
        transport.connect(settings.host, settings.port, settings.clientId)
        return attempt
    }

    /** Must hold [lock]. The running attempt, or a new one when the transport starts connecting on its own. */
    private fun attemptInProgress(): CompletableDeferred<MqttConnectionState> {
        val attempt = runningAttempt ?: CompletableDeferred()
        runningAttempt = attempt
        link = Link.CONNECTING
        return attempt
    }

    /** Must hold [lock]. Ends the running attempt, if any, with [result]. */
    private fun finishAttempt(newLink: Link, result: MqttConnectionState) {
        link = newLink
        runningAttempt?.complete(result)
        runningAttempt = null
    }

    private fun release(subscription: Subscription) = lock.locked {
        subscriptions -= subscription
        val filterStillUsed = subscriptions.any { it.filter == subscription.filter }
        if (!filterStillUsed && link == Link.CONNECTED) transport.unsubscribe(subscription.filter)
    }

    private inner class TransportEvents : MqttTransportListener {
        override fun onConnecting() {
            lock.locked { attemptInProgress() }
        }

        override fun onConnected() = lock.locked {
            finishAttempt(Link.CONNECTED, MqttConnectionState.CONNECTED)
            subscriptions.map { it.filter }.distinct().forEach(transport::subscribe)
            if (connectedBefore) reconnectEvents.tryEmit(Unit)
            connectedBefore = true
        }

        override fun onDisconnected() = lock.locked {
            finishAttempt(Link.DISCONNECTED, MqttConnectionState.FAILED)
            pendingAcks.values.forEach { it.completeExceptionally(MqttPublishException("The connection was lost")) }
            pendingAcks.clear()
        }

        override fun onPublishAcknowledged(messageId: Int) {
            lock.locked { pendingAcks.remove(messageId) }?.complete(Unit)
        }

        override fun onMessage(topic: String, payload: NSData) {
            val message = IotMessage(topic, payload.decodeUtf8())
            val receivers = lock.locked { subscriptions.filter { topicMatchesFilter(it.filter, topic) } }
            receivers.forEach { it.messages.trySend(message) }
        }
    }
}
