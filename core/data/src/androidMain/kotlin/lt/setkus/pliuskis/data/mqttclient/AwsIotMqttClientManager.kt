package lt.setkus.pliuskis.data.mqttclient

import co.touchlab.kermit.Logger
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedContext
import com.hivemq.client.mqtt.lifecycle.MqttDisconnectSource
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.future.future
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import lt.setkus.pliuskis.data.IotManager
import lt.setkus.pliuskis.data.aws.AwsIotCredentialsCache
import java.util.concurrent.TimeUnit

private const val PENDING_CONNECTION_TIMEOUT_MILLIS = 10_000L
private const val FIRST_RECONNECT_DELAY_MILLIS = 1_000L
private const val MAX_RECONNECT_DELAY_MILLIS = 120_000L
private const val MAX_BACKOFF_SHIFT = 7

/** The automatic reconnect's wait before attempt [attempt] (1, 2, ...): 1 s, doubling, at most 2 minutes. */
internal fun reconnectBackoffMillis(attempt: Int): Long {
    val doubled = FIRST_RECONNECT_DELAY_MILLIS shl (attempt - 1).coerceIn(0, MAX_BACKOFF_SHIFT)
    return doubled.coerceAtMost(MAX_RECONNECT_DELAY_MILLIS)
}

/**
 * The Android [IotManager] of release builds: HiveMQ over a SigV4-signed WebSocket to AWS IoT ([target], port 443),
 * with the signed-in identity's temporary Cognito [credentials]. The contract is [IotManager]'s; on top of it:
 *
 * - **A new URL for every attempt.** HiveMQ reconnects on its own after a lost connection, but with the same transport
 *   settings, so this client reconnects through HiveMQ's reconnector itself: before every attempt, its own or one a
 *   [connect] starts, it makes the credentials fresh and signs a new URL at that moment. HiveMQ can't carry a signed
 *   query (see [SignedWebSocketRelay]), so every attempt goes through the relay with its own one-time path.
 * - **Client ID.** `<Cognito identity ID>-<install suffix>`, the suffix being [settings]' client ID. HiveMQ fixes the
 *   client ID when the client is built, so a client is built for each identity, on its first connect.
 * - **Refused.** When AWS IoT answers the attempt but refuses it (the upgrade answered with HTTP 403, or the
 *   connection closed after the CONNECT and before any CONNACK: an identity without an AWS IoT policy), [connect]
 *   returns [MqttConnectionState.REFUSED] and the client makes no attempt on its own: only the next [connect] tries
 *   again, with new credentials. A socket error before any answer (no network, DNS) is
 *   [MqttConnectionState.FAILED], and the client keeps reconnecting with a growing wait ([reconnectDelayMillis]).
 * - **Signed out.** No attempt is made and no Cognito call; a connection that is open when the user signs out is
 *   closed, and nothing reconnects with the old identity.
 *
 * Subscriptions live on the current identity's client; HiveMQ subscribes again after its own reconnects.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class AwsIotMqttClientManager(
    private val settings: MqttSettings,
    private val credentials: AwsIotCredentialsCache,
    target: AwsIotWebSocketTarget,
    private val scope: CoroutineScope,
    private val pendingConnectionTimeoutMillis: Long = PENDING_CONNECTION_TIMEOUT_MILLIS,
    private val reconnectDelayMillis: (attempt: Int) -> Long = ::reconnectBackoffMillis,
) : IotManager {

    private val log = Logger.withTag("AwsIotMqttClient")
    private val relay = SignedWebSocketRelay(target)
    private val connectLock = Mutex()
    private val current = MutableStateFlow<Connection?>(null)

    init {
        credentials.onSignOut(::closeConnection)
        scope.coroutineContext[Job]?.invokeOnCompletion {
            current.value?.close()
            relay.close()
        }
    }

    override suspend fun connect(): MqttConnectionState {
        val state = withTimeoutOrNull(pendingConnectionTimeoutMillis) {
            connectLock.withLock { attemptToAwait() }?.await()
        }
        return state ?: MqttConnectionState.FAILED
    }

    override fun getClientId(): String = current.value?.clientId ?: settings.clientId

    override suspend fun publishString(message: String, topic: String) {
        val connection = checkNotNull(current.value) { "Not connected, so $topic was not sent" }
        connection.delegate.publishString(message, topic)
    }

    override fun subscribe(topic: String): Flow<IotMessage> = current.flatMapLatest { connection ->
        connection?.let {
            flow {
                connection.firstConnected.filter { it }.first()
                emitAll(connection.delegate.subscribe(topic))
            }
        } ?: emptyFlow()
    }

    override fun reconnects(): Flow<Unit> = current.flatMapLatest { it?.reconnectListener?.reconnects ?: emptyFlow() }

    /** Closes the open connection for good (the user signed out); the next [connect] starts from scratch. */
    private fun closeConnection() {
        val connection = current.value
        current.value = null
        connection?.close()
    }

    /**
     * Must hold [connectLock]. The attempt [connect] waits for: CONNECTED at once when connected, the running attempt,
     * the waiting reconnect started now, or a new client's first attempt; null when no attempt can be made.
     */
    private suspend fun attemptToAwait(): Deferred<MqttConnectionState>? {
        val connection = current.value?.takeUnless { it.closed }
        val running = connection?.attempt
        return when {
            connection != null && connection.client.state.isConnected ->
                CompletableDeferred(MqttConnectionState.CONNECTED)
            running != null -> running
            connection != null && connection.gate != null -> connection.reconnectNow()
            else -> newConnection()
        }
    }

    /**
     * A client for the signed-in identity, connecting with a URL signed now; null when signed out or without
     * credentials.
     */
    private suspend fun newConnection(): Deferred<MqttConnectionState>? {
        val clientId = credentials.clientIdForAttempt(settings.clientId)
        val signedUrl = clientId?.let { signedUrlOrNull() }
        return if (clientId != null && signedUrl != null) {
            current.value?.close()
            Connection(clientId, signedUrl).also { current.value = it }.start()
        } else {
            null
        }
    }

    private fun signedUrlOrNull(): String? = runCatching { credentials.signedUrlNow() }.getOrNull()

    /** The HiveMQ client of one identity, and the state of its connect attempts. */
    private inner class Connection(val clientId: String, firstSignedUrl: String) {
        val reconnectListener = ReconnectListener()
        val firstConnected = MutableStateFlow(false)

        /** The running attempt: from its start until its CONNACK or its end. */
        @Volatile
        var attempt: CompletableDeferred<MqttConnectionState>? = null

        /** Set while the client waits for its next attempt (a growing wait, or held after a refusal). */
        @Volatile
        var gate: CompletableDeferred<Unit>? = null

        @Volatile
        var closed = false

        @Volatile
        private var relayAttempt: RelayAttempt = relay.register(firstSignedUrl)

        val client: Mqtt3Client = MqttClient.builder()
            .identifier(clientId)
            .transportConfig(relayAttempt.transportConfig())
            .addConnectedListener(reconnectListener)
            .addConnectedListener { onConnected() }
            .addDisconnectedListener(::onDisconnected)
            .useMqttVersion3()
            .build()

        val delegate = HiveMqttClientManager(client)

        fun start(): Deferred<MqttConnectionState> {
            val started = beginAttempt()
            client.toAsync().connect().whenComplete { _, _ -> }
            return started
        }

        /** Starts the waiting reconnect now, for a [connect] call. */
        fun reconnectNow(): Deferred<MqttConnectionState> {
            val started = beginAttempt()
            gate?.complete(Unit)
            return started
        }

        fun close() {
            closed = true
            gate?.cancel()
            endAttempt(MqttConnectionState.FAILED)
            if (client.state.isConnected) client.toAsync().disconnect()
        }

        private fun beginAttempt(): CompletableDeferred<MqttConnectionState> =
            attempt ?: CompletableDeferred<MqttConnectionState>().also { attempt = it }

        private fun endAttempt(state: MqttConnectionState) {
            attempt?.complete(state)
            attempt = null
        }

        private fun onConnected() {
            endAttempt(MqttConnectionState.CONNECTED)
            firstConnected.value = true
            if (closed) client.toAsync().disconnect()
        }

        /** HiveMQ's event loop: the connection is gone or the attempt failed. Decides on the next attempt. */
        private fun onDisconnected(context: MqttClientDisconnectedContext) {
            val refused = attempt != null && relayAttempt.upstreamAnswered
            endAttempt(if (refused) MqttConnectionState.REFUSED else MqttConnectionState.FAILED)
            if (context.source != MqttDisconnectSource.USER && !closed) {
                if (refused) {
                    log.i { "AWS IoT refused the connection; no automatic reconnect" }
                    credentials.invalidateCredentials()
                }
                scheduleReconnect(context, firstWait = if (refused) null else reconnectDelayMillis(context.attempts()))
            }
        }

        /**
         * Reconnects through HiveMQ's reconnector with a URL signed for that attempt. The attempt waits for [firstWait]
         * (or for a [connect], whichever comes first), or, with no wait, only for a [connect].
         */
        private fun scheduleReconnect(context: MqttClientDisconnectedContext, firstWait: Long?) {
            val reconnector = context.reconnector
            val firstGate = CompletableDeferred<Unit>().also { gate = it }
            reconnector.reconnect(true).delay(0, TimeUnit.MILLISECONDS)
            reconnector.reconnectWhen(scope.future { nextSignedUrl(firstGate, firstWait) }) { signedUrl, _ ->
                gate = null
                if (signedUrl != null && !closed) {
                    relayAttempt = relay.register(signedUrl)
                    reconnector.transportConfig(relayAttempt.transportConfig())
                } else {
                    reconnector.reconnect(false)
                    endAttempt(MqttConnectionState.FAILED)
                }
            }
        }

        /**
         * Waits for the next attempt, then makes the credentials fresh and signs its URL. When Cognito gives no
         * credentials, that attempt fails and the next one waits again; null when the client is closed or the user
         * signed in as someone else.
         */
        private suspend fun nextSignedUrl(firstGate: CompletableDeferred<Unit>, firstWait: Long?): String? {
            var waitGate = firstGate
            var wait = firstWait
            var failedTries = 0
            var signedUrl: String? = null
            while (signedUrl == null && !closed) {
                if (wait == null) waitGate.await() else withTimeoutOrNull(wait) { waitGate.await() }
                gate = null
                beginAttempt()
                val attemptClientId = credentials.clientIdForAttempt(settings.clientId)
                if (attemptClientId != null && attemptClientId != clientId) closed = true
                signedUrl = if (attemptClientId == clientId) signedUrlOrNull() else null
                if (signedUrl == null && !closed) {
                    endAttempt(MqttConnectionState.FAILED)
                    failedTries++
                    wait = reconnectDelayMillis(failedTries)
                    waitGate = CompletableDeferred<Unit>().also { gate = it }
                }
            }
            return signedUrl
        }
    }
}

/** How many reconnect attempts HiveMQ made since the last connection (0 after a lost connection), plus this one. */
private fun MqttClientDisconnectedContext.attempts(): Int = reconnector.attempts + 1
