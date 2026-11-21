package lt.setkus.pliuskis.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import lt.setkus.pliuskis.core.presentation.control.ControlUiState
import lt.setkus.pliuskis.core.presentation.control.ControlViewModel
import lt.setkus.pliuskis.core.presentation.devices.DevicesListScreenState
import lt.setkus.pliuskis.core.presentation.devices.DevicesViewModel
import lt.setkus.pliuskis.data.mqttclient.MqttSettings
import lt.setkus.pliuskis.data.mqttclient.MqttTransport
import lt.setkus.pliuskis.data.mqttclient.MqttTransportListener
import lt.setkus.pliuskis.data.mqttclient.TlsMode
import org.koin.core.context.stopKoin
import platform.Foundation.NSData
import kotlin.reflect.KClass

private val settings = MqttSettings(
    clientId = "story10test",
    host = "localhost",
    port = 1883,
    tlsMode = TlsMode.NONE,
)

/** A broker that refuses every connection: each connect attempt ends at once with a disconnect. */
private class RefusingMqttTransport : MqttTransport {
    override var listener: MqttTransportListener? = null

    override fun connect(host: String, port: Int, clientId: String) {
        listener?.onDisconnected()
    }

    override fun subscribe(topicFilter: String) = Unit

    override fun unsubscribe(topicFilter: String) = Unit

    override fun publish(topic: String, payload: NSData): Int = -1
}

/**
 * Keeps the ViewModels a test creates through the Swift-facing factories, so the test can clear them (which cancels
 * their viewModelScope) before Dispatchers.Main is reset.
 */
private class SharedHarness {
    private val store = ViewModelStore()

    fun <VM : ViewModel> keep(viewModelClass: KClass<VM>, create: () -> VM): VM =
        ViewModelProvider.create(
            store,
            viewModelFactory { addInitializer(viewModelClass) { create() } },
        )[viewModelClass]

    fun clear() = store.clear()
}

/**
 * Runs [block] with Koin started through [initShared] and a test Main dispatcher. Koin is a process-wide singleton,
 * so every test stops it again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun runWithShared(block: suspend TestScope.(SharedHarness) -> Unit) {
    val dispatcher = StandardTestDispatcher()
    Dispatchers.setMain(dispatcher)
    try {
        initShared(settings, RefusingMqttTransport())
        runTest(dispatcher) {
            val harness = SharedHarness()
            try {
                block(harness)
            } finally {
                harness.clear()
            }
        }
    } finally {
        stopKoin()
        Dispatchers.resetMain()
    }
}

/**
 * STORY-10: the Swift-facing entry point of the `Shared` framework, called the way Swift calls it. `initShared`
 * starts the iOS Koin graph; the factories create the ViewModels without Swift touching Koin. The graph gets a
 * transport whose broker refuses every connection, so connecting fails: the device list must end in an Error state,
 * not a thrown exception (AC49). AC50's Swift test (`shared/swift-test`) checks the same first state from
 * Swift.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Story10SharedEntryPointTest : FunSpec({

    // AC50 (Kotlin side)
    // GIVEN initShared(settings) has started Koin
    // WHEN a DevicesViewModel is created through createDevicesViewModel()
    // THEN its first state is Loading
    test("testStory10_devicesViewModelFromFactoryStartsLoading") {
        runWithShared { harness ->
            val target = harness.keep(DevicesViewModel::class) { createDevicesViewModel() }

            target.devices.value shouldBe DevicesListScreenState.Loading
        }
    }

    // AC49
    // GIVEN the iOS graph's transport refuses the connection (connect fails)
    // WHEN the device list is collected
    // THEN it shows Loading, then Error, and the flow does not throw
    test("testStory10_devicesViewModelWithoutIosMqttClientEndsInErrorState") {
        runWithShared { harness ->
            val target = harness.keep(DevicesViewModel::class) { createDevicesViewModel() }

            target.devices.test {
                awaitItem() shouldBe DevicesListScreenState.Loading
                awaitItem().shouldBeInstanceOf<DevicesListScreenState.Error>()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // AC50 (Kotlin side)
    // GIVEN initShared(settings) has started Koin
    // WHEN a ControlViewModel is created through createControlViewModel(deviceId)
    // THEN its first state is Loading
    test("testStory10_controlViewModelFromFactoryStartsLoading") {
        runWithShared { harness ->
            val target = harness.keep(ControlViewModel::class) { createControlViewModel("pico-1") }

            target.controlUiState.value shouldBe ControlUiState.Loading
        }
    }
})
