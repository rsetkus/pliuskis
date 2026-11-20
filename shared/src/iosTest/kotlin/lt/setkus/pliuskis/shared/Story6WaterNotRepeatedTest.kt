package lt.setkus.pliuskis.shared

import androidx.lifecycle.ViewModelStore
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import lt.setkus.pliuskis.data.mqttclient.MqttSettings
import lt.setkus.pliuskis.data.mqttclient.TlsMode

private const val DEVICE_ID = "pico-1"
private const val WATER_TOPIC = "devices/pico-1/command"
private const val WATER_PAYLOAD = """{"command":"water"}"""
private const val STATUS_REQUEST_TOPIC = "devices/pico-1/status"

/** Longer than every wait in the app: the 10 s connect wait, the 3 s empty-list timeout. */
private const val SETTLE_MILLIS = 15_000L

private val settings = MqttSettings(
    clientId = "story6water",
    host = "localhost",
    port = 1883,
    tlsMode = TlsMode.NONE,
)

/**
 * STORY-6 AC37: a water tap is never sent twice by a foreground return or a reconnect. The test drives the real iOS
 * graph (`initShared` with the iOS MQTT client over a fake transport that plays the broker), taps water on pico-1's
 * control ViewModel from the Swift-facing factory, sends the app to the background (the socket dies), and brings it
 * back: the visible control screen refreshes (Swift: `viewModel.refresh()`) and the broker accepts the reconnect.
 * The transport records every PUBLISH: there is exactly one on the water topic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Story6WaterNotRepeatedTest : FunSpec({

    // AC37, the app reconnects on its foreground refresh
    // GIVEN the user tapped water on pico-1 once, then the app went to the background and its socket died
    // WHEN the app becomes active, the control screen refreshes and the broker accepts the new connection
    // THEN the transport saw exactly one water command, and the reconnect did happen (a new status request went out)
    test("testStory6_foregroundRefreshAndReconnectPublishNoSecondWaterCommand") {
        val broker = RetainingBrokerTransport()
        runWithBroker(settings, broker) {
            val store = ViewModelStore()
            try {
                val target = keepControlViewModel(store, DEVICE_ID)
                backgroundScope.launch { target.controlUiState.collect {} }
                runCurrent()
                broker.acceptConnection()
                runCurrent()
                target.waterPlant()
                runCurrent()
                broker.publishesOn(WATER_TOPIC).map { it.payload } shouldBe listOf(WATER_PAYLOAD)

                broker.dropConnection()
                runCurrent()
                target.refresh()
                runCurrent()
                broker.acceptConnection()
                runCurrent()
                advanceTimeBy(SETTLE_MILLIS)
                runCurrent()

                broker.publishesOn(STATUS_REQUEST_TOPIC).size shouldBeGreaterThanOrEqual 2
                broker.publishesOn(WATER_TOPIC).map { it.payload } shouldBe listOf(WATER_PAYLOAD)
            } finally {
                store.clear()
            }
        }
    }

    // AC37, CocoaMQTT reconnects on its own before the scene becomes active
    // GIVEN the user tapped water on pico-1 once, then the app went to the background and its socket died
    // WHEN CocoaMQTT's automatic reconnect gets a CONNACK, and then the app becomes active and the control screen
    //   refreshes on the connected client
    // THEN the transport saw exactly one water command
    test("testStory6_automaticReconnectThenForegroundRefreshPublishNoSecondWaterCommand") {
        val broker = RetainingBrokerTransport()
        runWithBroker(settings, broker) {
            val store = ViewModelStore()
            try {
                val target = keepControlViewModel(store, DEVICE_ID)
                backgroundScope.launch { target.controlUiState.collect {} }
                runCurrent()
                broker.acceptConnection()
                runCurrent()
                target.waterPlant()
                runCurrent()
                broker.publishesOn(WATER_TOPIC).size shouldBe 1

                broker.dropConnection()
                runCurrent()
                broker.startAutomaticReconnect()
                broker.acceptConnection()
                runCurrent()
                target.refresh()
                runCurrent()
                advanceTimeBy(SETTLE_MILLIS)
                runCurrent()

                broker.publishesOn(WATER_TOPIC).map { it.payload } shouldBe listOf(WATER_PAYLOAD)
            } finally {
                store.clear()
            }
        }
    }
})
