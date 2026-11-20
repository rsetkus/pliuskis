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
 *
 * The broker settings come from [settings]; iOS connects in plaintext (`TlsMode.NONE`), so the TLS mode is not used.
 * The transport calls back on the main queue and the callers may be on any thread, so the state is guarded by a lock.
 */
internal class IosMqttClientManager(
    private val transport: MqttTransport,
    private val settings: MqttSettings,
    private val pendingConnectionTimeoutMillis: Long = PENDING_CONNECTION_TIMEOUT_MILLIS,
) : IotManager {

    private enum class Link { DISCONNECTED, CONNECTING, CONNECTED }

    private class Subscription(val filter: String, val messages: SendChannel<IotMessage>)

    private val lock = NSRecursiveLock()
    private var link = Link.DISCONNECTED
    private var runningAttempt: CompletableDeferred<MqttConnectionState>? = null
    private val subscriptions = mutableListOf<Subscription>()
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
        transport.publish(topic, message.toUtf8NSData())
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
        }

        override fun onMessage(topic: String, payload: NSData) {
            val message = IotMessage(topic, payload.decodeUtf8())
            val receivers = lock.locked { subscriptions.filter { topicMatchesFilter(it.filter, topic) } }
            receivers.forEach { it.messages.trySend(message) }
        }
    }
}
