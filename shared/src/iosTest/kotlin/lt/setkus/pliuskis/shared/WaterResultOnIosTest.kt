package lt.setkus.pliuskis.shared

import androidx.lifecycle.ViewModelStore
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import lt.setkus.pliuskis.core.presentation.control.WateringState
import lt.setkus.pliuskis.data.mqttclient.MqttSettings
import lt.setkus.pliuskis.data.mqttclient.TlsMode

private const val DEVICE_ID = "pico-1"
private const val WATER_TOPIC = "devices/pico-1/command"

private val settings = MqttSettings(
    clientId = "waterresult",
    host = "localhost",
    port = 1883,
    tlsMode = TlsMode.NONE,
)

/**
 * The water button's result on iOS follows the broker, as on Android: the real iOS graph (`initShared` with the iOS
 * MQTT client over a fake transport that plays the broker) shows "Watering started" only when the broker took the
 * command, and "Try again" when the connection is gone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WaterResultOnIosTest : FunSpec({

    // GIVEN the control screen of pico-1 is open and connected
    // WHEN the user taps water and the broker acknowledges the command
    // THEN the watering state is STARTED
    test("waterWhileConnectedEndsInStarted") {
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
                target.wateringState.value shouldBe WateringState.STARTED
            } finally {
                store.clear()
            }
        }
    }

    // GIVEN the control screen of pico-1 was connected and the connection is gone (Wi-Fi off, broker down)
    // WHEN the user taps water
    // THEN the watering state is FAILED, not STARTED
    test("waterAfterTheConnectionDroppedEndsInFailed") {
        val broker = RetainingBrokerTransport()
        runWithBroker(settings, broker) {
            val store = ViewModelStore()
            try {
                val target = keepControlViewModel(store, DEVICE_ID)
                backgroundScope.launch { target.controlUiState.collect {} }
                runCurrent()
                broker.acceptConnection()
                runCurrent()
                broker.dropConnection()
                runCurrent()

                target.waterPlant()
                runCurrent()

                target.wateringState.value shouldBe WateringState.FAILED
            } finally {
                store.clear()
            }
        }
    }
})
