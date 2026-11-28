package lt.setkus.pliuskis.shared

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import lt.setkus.pliuskis.core.presentation.control.ControlViewModel
import lt.setkus.pliuskis.core.presentation.devices.DevicesViewModel
import lt.setkus.pliuskis.data.mqttclient.MqttSettings
import lt.setkus.pliuskis.data.mqttclient.MqttTransport
import lt.setkus.pliuskis.data.mqttclient.MqttTransportListener
import org.koin.core.context.stopKoin
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

/**
 * Stands in for the Swift CocoaMQTT transport and plays a small broker behind it, so a test can drive the real iOS
 * graph (`initShared`) through an app background / foreground cycle:
 *
 * - it records every CONNECT ([connectCalls]), SUBSCRIBE ([subscribes]) and PUBLISH ([publishes]) the app sends,
 *   and answers a PUBLISH made while connected with PUBACK on the Main dispatcher, as CocoaMQTT calls back on the main
 *   queue (a PUBLISH while disconnected is queued and never acknowledged, as in CocoaMQTT);
 * - the test decides when the broker answers a CONNECT ([acceptConnection]) and when the socket dies
 *   ([dropConnection], what happens to the socket while iOS suspends the app);
 * - like Mosquitto it keeps retained messages ([retain]): a SUBSCRIBE while connected gets every matching retained
 *   message at once, and a retained message set while connected goes to every matching active subscription. A clean
 *   session forgets the subscriptions when the connection drops.
 */
internal class RetainingBrokerTransport : MqttTransport {

    class Publish(val topic: String, val payload: String)

    override var listener: MqttTransportListener? = null

    var connectCalls = 0
        private set
    private var lastMessageId = 0
    val subscribes = mutableListOf<String>()
    val publishes = mutableListOf<Publish>()

    private var connected = false
    private val activeFilters = mutableListOf<String>()
    private val retained = linkedMapOf<String, String>()

    override fun connect(host: String, port: Int, clientId: String) {
        connectCalls++
        requireListener().onConnecting()
    }

    override fun disconnect() = Unit

    override fun subscribe(topicFilter: String) {
        subscribes += topicFilter
        if (connected) {
            activeFilters += topicFilter
            retained.filterKeys { matches(topicFilter, it) }.forEach { (topic, payload) -> send(topic, payload) }
        }
    }

    override fun unsubscribe(topicFilter: String) {
        activeFilters -= topicFilter
    }

    override fun publish(topic: String, payload: NSData): Int {
        publishes += Publish(topic, payload.decodeUtf8())
        val messageId = ++lastMessageId
        if (connected) {
            CoroutineScope(Dispatchers.Main).launch { requireListener().onPublishAcknowledged(messageId) }
        }
        return messageId
    }

    /** The broker accepts the running CONNECT: a CONNACK with return code 0. */
    fun acceptConnection() {
        connected = true
        requireListener().onConnected()
    }

    /** The socket is gone (iOS suspended the app, broker restart). The clean session loses its subscriptions. */
    fun dropConnection() {
        connected = false
        activeFilters.clear()
        requireListener().onDisconnected()
    }

    /** CocoaMQTT starts an automatic reconnect on its own (no CONNECT asked for by Kotlin). */
    fun startAutomaticReconnect() = requireListener().onConnecting()

    /** A device (or its Last Will) sets the retained message of [topic]; live subscribers get it at once. */
    fun retain(topic: String, payload: String) {
        retained[topic] = payload
        if (connected && activeFilters.any { matches(it, topic) }) send(topic, payload)
    }

    fun publishesOn(topic: String): List<Publish> = publishes.filter { it.topic == topic }

    private fun send(topic: String, payload: String) = requireListener().onMessage(topic, payload.encodeUtf8())

    private fun requireListener(): MqttTransportListener =
        checkNotNull(listener) { "initShared never set a listener on the transport" }

    /** MQTT filter matching for the filters the app uses: exact levels and the single-level wildcard `+`. */
    private fun matches(filter: String, topic: String): Boolean {
        val filterLevels = filter.split("/")
        val topicLevels = topic.split("/")
        return filterLevels.size == topicLevels.size &&
            filterLevels.zip(topicLevels).all { (f, t) -> f == "+" || f == t }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun String.encodeUtf8(): NSData {
    val bytes = encodeToByteArray()
    return if (bytes.isEmpty()) {
        NSData()
    } else {
        bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.decodeUtf8(): String {
    val bytes = ByteArray(length.toInt())
    if (bytes.isNotEmpty()) {
        bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    }
    return bytes.decodeToString()
}

/**
 * Runs [block] with the shared graph started by `initShared(settings, transport)`, as the iOS app starts it, and a
 * test Main dispatcher whose virtual clock [block] controls. Koin is process-wide, so the graph is stopped again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun runWithBroker(
    settings: MqttSettings,
    transport: RetainingBrokerTransport,
    block: suspend TestScope.() -> Unit,
) {
    val dispatcher = StandardTestDispatcher()
    Dispatchers.setMain(dispatcher)
    try {
        initShared(settings, transport)
        runTest(dispatcher) { block() }
    } finally {
        stopKoin()
        Dispatchers.resetMain()
    }
}

/** Creates the device list ViewModel through the Swift-facing factory and keeps it in [store], as a screen does. */
internal fun keepDevicesViewModel(store: ViewModelStore): DevicesViewModel =
    ViewModelProvider.create(
        store,
        viewModelFactory { addInitializer(DevicesViewModel::class) { createDevicesViewModel() } },
    )[DevicesViewModel::class]

/** Creates the control ViewModel of [deviceId] through the Swift-facing factory and keeps it in [store]. */
internal fun keepControlViewModel(store: ViewModelStore, deviceId: String): ControlViewModel =
    ViewModelProvider.create(
        store,
        viewModelFactory { addInitializer(ControlViewModel::class) { createControlViewModel(deviceId) } },
    )[ControlViewModel::class]
