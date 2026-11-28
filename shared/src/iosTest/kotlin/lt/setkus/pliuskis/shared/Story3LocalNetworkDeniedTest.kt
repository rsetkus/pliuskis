package lt.setkus.pliuskis.shared

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.viewModelFactory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import lt.setkus.pliuskis.core.presentation.devices.DevicesListScreenState
import lt.setkus.pliuskis.core.presentation.devices.DevicesViewModel
import lt.setkus.pliuskis.data.mqttclient.MqttSettings
import lt.setkus.pliuskis.data.mqttclient.MqttTransport
import lt.setkus.pliuskis.data.mqttclient.MqttTransportListener
import lt.setkus.pliuskis.data.mqttclient.TlsMode
import org.koin.core.context.stopKoin
import platform.Foundation.NSData

private const val CONNECT_DEADLINE_MILLIS = 10_000L

private val settings = MqttSettings(
    clientId = "story3client",
    host = "story3-broker",
    port = 1883,
    tlsMode = TlsMode.NONE,
)

/**
 * Stands in for the Swift CocoaMQTT transport when iOS denies the app Local Network access. The socket then never
 * reaches the broker: either no answer comes at all ([neverAnswers]), or the transport reports the connection as
 * gone ([reportDenied], what CocoaMQTT's didDisconnect does after the socket error).
 */
private class Story3DeniedLocalNetworkTransport : MqttTransport {
    override var listener: MqttTransportListener? = null

    var connectRequested = false
        private set

    override fun connect(host: String, port: Int, clientId: String) {
        connectRequested = true
    }

    override fun disconnect() = Unit

    override fun subscribe(topicFilter: String) = Unit

    override fun unsubscribe(topicFilter: String) = Unit

    override fun publish(topic: String, payload: NSData): Int = -1

    /** The socket failed: local network access is denied. */
    fun reportDenied() =
        checkNotNull(listener) { "initShared never set a listener on the transport" }.onDisconnected()
}

/**
 * Runs [block] with the shared graph started by `initShared(settings, transport)`, as the iOS app starts it, and a
 * test Main dispatcher whose virtual clock [block] controls. Koin is process-wide, so the graph is stopped again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun runWithDeniedLocalNetwork(
    transport: Story3DeniedLocalNetworkTransport,
    block: suspend TestScope.(DevicesViewModel) -> Unit,
) {
    val dispatcher = StandardTestDispatcher()
    Dispatchers.setMain(dispatcher)
    val store = ViewModelStore()
    try {
        initShared(settings, transport)
        runTest(dispatcher) {
            val target = ViewModelProvider.create(
                store,
                viewModelFactory { addInitializer(DevicesViewModel::class) { createDevicesViewModel() } },
            )[DevicesViewModel::class]
            // The device list screen collects the state while it is shown.
            backgroundScope.launch { target.devices.collect {} }
            runCurrent()
            try {
                block(target)
            } finally {
                store.clear()
            }
        }
    } finally {
        stopKoin()
        Dispatchers.resetMain()
    }
}

/**
 * STORY-3 AC21: when iOS denies Local Network access, the device list ends in its Error state (with retry), never
 * stuck on Loading, within 10 s of the connect call. The graph is the real iOS one (`initShared` with the iOS MQTT
 * client over the Swift transport); only the transport is a fake.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Story3LocalNetworkDeniedTest : FunSpec({

    // AC21
    // GIVEN local network access is denied and the transport never gets an answer from the broker
    // WHEN the device list connects
    // THEN the state is Error, not Loading, 10 s after the connect call
    test("testStory3_deviceListIsErrorWithinTenSecondsWhenTransportNeverConnects") {
        val transport = Story3DeniedLocalNetworkTransport()
        runWithDeniedLocalNetwork(transport) { target ->
            transport.connectRequested shouldBe true
            target.devices.value shouldBe DevicesListScreenState.Loading

            advanceTimeBy(CONNECT_DEADLINE_MILLIS)
            runCurrent()

            target.devices.value.shouldBeInstanceOf<DevicesListScreenState.Error>()
        }
    }

    // AC21
    // GIVEN local network access is denied and the transport reports the connection as gone right away
    // WHEN the device list connects
    // THEN the state is Error at once, well within 10 s
    test("testStory3_deviceListIsErrorWhenTransportReportsTheDeniedConnectionAsGone") {
        val transport = Story3DeniedLocalNetworkTransport()
        runWithDeniedLocalNetwork(transport) { target ->
            transport.connectRequested shouldBe true

            transport.reportDenied()
            runCurrent()

            target.devices.value.shouldBeInstanceOf<DevicesListScreenState.Error>()
        }
    }

    // AC21 + edge case "allowing the permission later in Settings and tapping retry connects"
    // GIVEN the device list is in Error after a denied connect
    // WHEN the user allows Local Network, taps retry and the broker accepts the new attempt
    // THEN the state leaves Error: no device arrives, so it is the empty list after the 3 s timeout
    test("testStory3_retryAfterLocalNetworkIsAllowedLeavesTheErrorState") {
        val transport = Story3DeniedLocalNetworkTransport()
        runWithDeniedLocalNetwork(transport) { target ->
            transport.reportDenied()
            runCurrent()
            target.devices.value.shouldBeInstanceOf<DevicesListScreenState.Error>()

            target.retry()
            runCurrent()
            checkNotNull(transport.listener) { "initShared never set a listener on the transport" }.onConnected()
            runCurrent()
            advanceTimeBy(CONNECT_DEADLINE_MILLIS)
            runCurrent()

            target.devices.value shouldBe DevicesListScreenState.Success(emptyList())
        }
    }
})
