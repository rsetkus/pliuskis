package lt.setkus.pliuskis.shared

import androidx.lifecycle.ViewModelStore
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import lt.setkus.pliuskis.core.devicelist.DeviceType
import lt.setkus.pliuskis.core.presentation.devices.DeviceListItem
import lt.setkus.pliuskis.core.presentation.devices.DevicesListScreenState
import lt.setkus.pliuskis.data.mqttclient.MqttSettings
import lt.setkus.pliuskis.data.mqttclient.TlsMode

private const val CLIENT_ID = "story6client"
private const val DEVICE_ID = "pico-1"
private const val INFO_TOPIC = "devices/pico-1/info"
private const val AVAILABILITY_TOPIC = "devices/pico-1/availability"
private const val INFO_PAYLOAD = """{"name":"Basil","deviceId":"pico-1","type":"plant"}"""
private const val INFO_FILTER = "devices/+/info"
private const val AVAILABILITY_FILTER = "devices/+/availability"
private const val STATUS_REQUEST_TOPIC = "devices/pico-1/status"
private const val STATUS_REQUEST_PAYLOAD = """{"applicationId":"story6client"}"""

/** Longer than every wait in the app: the 10 s connect wait, the 3 s empty-list timeout. */
private const val SETTLE_MILLIS = 15_000L

private val settings = MqttSettings(
    clientId = CLIENT_ID,
    host = "localhost",
    port = 1883,
    tlsMode = TlsMode.NONE,
)

private val basilOnline = DeviceListItem("Basil", DEVICE_ID, DeviceType.PLANT, online = true)
private val basilOffline = DeviceListItem("Basil", DEVICE_ID, DeviceType.PLANT, online = false)

/**
 * STORY-6 AC36: when the app returns to the foreground, SwiftUI's scenePhase handler calls `refresh()` on the visible
 * screen's shared ViewModel (Swift: `viewModel.refresh()`). The tests drive the real iOS graph, started by
 * `initShared` with the iOS MQTT client over a fake transport that plays the broker, and create the ViewModels through
 * the Swift-facing factories. The transport counts the CONNECT packets the app sends:
 *
 * - disconnected (iOS killed the socket while the app was suspended): exactly one CONNECT, and the screen's data is
 *   requested again after the CONNACK (the device list subscribes again and shows the broker's retained state; the
 *   control screen publishes a new status request);
 * - still connected: no CONNECT at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Story6ForegroundReconnectTest : FunSpec({

    // AC36, device list, disconnected
    // GIVEN the device list showed Basil online, then the app went to the background and its socket died, and while
    //   it was away Basil went offline (the broker's retained availability is now "offline")
    // WHEN the app becomes active and the visible device list refreshes
    // THEN the app sends exactly one CONNECT, subscribes to both discovery filters after the CONNACK, and the list
    //   shows Basil offline, without a manual retry
    test("testStory6_deviceListReconnectsOnceAndShowsDeviceThatWentOfflineInBackground") {
        val broker = RetainingBrokerTransport()
        broker.retain(INFO_TOPIC, INFO_PAYLOAD)
        broker.retain(AVAILABILITY_TOPIC, "online")
        runWithBroker(settings, broker) {
            val store = ViewModelStore()
            try {
                val target = keepDevicesViewModel(store)
                backgroundScope.launch { target.devices.collect {} }
                runCurrent()
                broker.acceptConnection()
                runCurrent()
                target.devices.value shouldBe DevicesListScreenState.Success(listOf(basilOnline))
                val connectsBeforeBackground = broker.connectCalls

                broker.dropConnection()
                runCurrent()
                broker.retain(AVAILABILITY_TOPIC, "offline")

                target.refresh()
                runCurrent()
                broker.connectCalls shouldBe connectsBeforeBackground + 1
                val subscribesBeforeConnack = broker.subscribes.size
                broker.acceptConnection()
                runCurrent()
                advanceTimeBy(SETTLE_MILLIS)
                runCurrent()

                broker.connectCalls shouldBe connectsBeforeBackground + 1
                val subscribesAfterConnack = broker.subscribes.drop(subscribesBeforeConnack)
                subscribesAfterConnack shouldContainAll listOf(INFO_FILTER, AVAILABILITY_FILTER)
                target.devices.value shouldBe DevicesListScreenState.Success(listOf(basilOffline))
            } finally {
                store.clear()
            }
        }
    }

    // AC36, device list, connected
    // GIVEN the device list shows Basil online and the client is still connected (a short trip to the background)
    // WHEN the app becomes active and the visible device list refreshes
    // THEN no CONNECT is sent and the list still shows Basil online
    test("testStory6_deviceListRefreshWhileConnectedSendsNoSecondConnect") {
        val broker = RetainingBrokerTransport()
        broker.retain(INFO_TOPIC, INFO_PAYLOAD)
        broker.retain(AVAILABILITY_TOPIC, "online")
        runWithBroker(settings, broker) {
            val store = ViewModelStore()
            try {
                val target = keepDevicesViewModel(store)
                backgroundScope.launch { target.devices.collect {} }
                runCurrent()
                broker.acceptConnection()
                runCurrent()
                target.devices.value shouldBe DevicesListScreenState.Success(listOf(basilOnline))
                val connectsBeforeForeground = broker.connectCalls

                target.refresh()
                runCurrent()
                advanceTimeBy(SETTLE_MILLIS)
                runCurrent()

                broker.connectCalls shouldBe connectsBeforeForeground
                target.devices.value shouldBe DevicesListScreenState.Success(listOf(basilOnline))
            } finally {
                store.clear()
            }
        }
    }

    // AC36, control screen, disconnected
    // GIVEN the control screen of pico-1 had requested its status once, then the app went to the background and its
    //   socket died
    // WHEN the app becomes active and the visible control screen refreshes
    // THEN the app sends exactly one CONNECT and, once the broker accepts it, publishes a new status request for
    //   pico-1 (with this install's applicationId). The refresh and the reconnect (AC44) may each ask once.
    test("testStory6_controlScreenReconnectsOnceAndRequestsStatusAgain") {
        val broker = RetainingBrokerTransport()
        runWithBroker(settings, broker) {
            val store = ViewModelStore()
            try {
                val target = keepControlViewModel(store, DEVICE_ID)
                backgroundScope.launch { target.controlUiState.collect {} }
                runCurrent()
                broker.acceptConnection()
                runCurrent()
                broker.publishesOn(STATUS_REQUEST_TOPIC).size shouldBe 1
                val connectsBeforeBackground = broker.connectCalls

                broker.dropConnection()
                runCurrent()

                target.refresh()
                runCurrent()
                broker.connectCalls shouldBe connectsBeforeBackground + 1
                broker.publishesOn(STATUS_REQUEST_TOPIC).size shouldBe 1
                broker.acceptConnection()
                runCurrent()
                advanceTimeBy(SETTLE_MILLIS)
                runCurrent()

                broker.connectCalls shouldBe connectsBeforeBackground + 1
                val requests = broker.publishesOn(STATUS_REQUEST_TOPIC)
                requests.size shouldBeGreaterThanOrEqual 2
                requests.last().payload shouldBe STATUS_REQUEST_PAYLOAD
            } finally {
                store.clear()
            }
        }
    }

    // AC36, control screen, connected
    // GIVEN the control screen of pico-1 is shown and the client is still connected
    // WHEN the app becomes active and the visible control screen refreshes
    // THEN no CONNECT is sent, and the screen's data is requested again: one new status request for pico-1
    test("testStory6_controlScreenRefreshWhileConnectedSendsNoConnectButRequestsStatusAgain") {
        val broker = RetainingBrokerTransport()
        runWithBroker(settings, broker) {
            val store = ViewModelStore()
            try {
                val target = keepControlViewModel(store, DEVICE_ID)
                backgroundScope.launch { target.controlUiState.collect {} }
                runCurrent()
                broker.acceptConnection()
                runCurrent()
                val connectsBeforeForeground = broker.connectCalls
                broker.publishesOn(STATUS_REQUEST_TOPIC).size shouldBe 1

                target.refresh()
                runCurrent()
                advanceTimeBy(SETTLE_MILLIS)
                runCurrent()

                broker.connectCalls shouldBe connectsBeforeForeground
                broker.publishesOn(STATUS_REQUEST_TOPIC).size shouldBe 2
            } finally {
                store.clear()
            }
        }
    }

    // AC44, on the real iOS client
    // GIVEN the control screen of pico-1 is open and connected, and has requested its status once
    // WHEN the connection drops and CocoaMQTT reconnects on its own (a new CONNACK), with no foreground return
    // THEN no CONNECT comes from Kotlin, and exactly one new status request for pico-1 follows that CONNACK
    test("testStory6_controlScreenRequestsStatusOnceAfterAutomaticReconnectWithoutRefresh") {
        val broker = RetainingBrokerTransport()
        runWithBroker(settings, broker) {
            val store = ViewModelStore()
            try {
                val target = keepControlViewModel(store, DEVICE_ID)
                backgroundScope.launch { target.controlUiState.collect {} }
                runCurrent()
                broker.acceptConnection()
                runCurrent()
                broker.publishesOn(STATUS_REQUEST_TOPIC).size shouldBe 1
                val connectsBeforeDrop = broker.connectCalls

                broker.dropConnection()
                runCurrent()
                broker.startAutomaticReconnect()
                runCurrent()
                broker.publishesOn(STATUS_REQUEST_TOPIC).size shouldBe 1
                broker.acceptConnection()
                runCurrent()
                advanceTimeBy(SETTLE_MILLIS)
                runCurrent()

                broker.connectCalls shouldBe connectsBeforeDrop
                val requests = broker.publishesOn(STATUS_REQUEST_TOPIC)
                requests.size shouldBe 2
                requests.last().payload shouldBe STATUS_REQUEST_PAYLOAD
            } finally {
                store.clear()
            }
        }
    }
})
