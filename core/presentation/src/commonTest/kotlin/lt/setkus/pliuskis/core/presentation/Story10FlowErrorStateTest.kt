package lt.setkus.pliuskis.core.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import lt.setkus.pliuskis.core.command.CommandExecutable
import lt.setkus.pliuskis.core.command.water.ExecuteWaterCommandUseCase
import lt.setkus.pliuskis.core.connect.ConnectUseCase
import lt.setkus.pliuskis.core.connect.Connectable
import lt.setkus.pliuskis.core.devicelist.DeviceEvent
import lt.setkus.pliuskis.core.devicelist.DeviceSubscribable
import lt.setkus.pliuskis.core.devicelist.ObserveDevicesUseCase
import lt.setkus.pliuskis.core.presentation.control.ControlUiState
import lt.setkus.pliuskis.core.presentation.control.ControlViewModel
import lt.setkus.pliuskis.core.presentation.control.DeviceStatusMapper
import lt.setkus.pliuskis.core.presentation.devices.DevicesListScreenState
import lt.setkus.pliuskis.core.presentation.devices.DevicesViewModel
import lt.setkus.pliuskis.core.presentation.devices.fromDomainModel
import lt.setkus.pliuskis.core.systemstate.GetSystemStatusUseUseCase
import lt.setkus.pliuskis.core.systemstate.RequestSystemStateUseCase
import lt.setkus.pliuskis.core.systemstate.SystemStateRequestable
import lt.setkus.pliuskis.core.systemstate.SystemStateSubscribable

private const val DEVICE_ID = "pico-1"
private const val STATUS_PAYLOAD =
    """{"humidity":55,"waterLevel":70,"timestamp":{"epochSeconds":1700000000,"nanosecondsOfSecond":5}}"""
private const val INFO_PAYLOAD = """{"name":"Basil","deviceId":"pico-1","type":"plant"}"""

private class StreamBroken : IllegalStateException("MQTT stream broke")

/**
 * Emits [first], then throws [StreamBroken] once [breakStream] completes. The pause lets the collector see [first]
 * before the error; a StateFlow would otherwise conflate the two.
 */
private fun <T> streamThatBreaks(first: T, breakStream: CompletableDeferred<Unit>): Flow<T> = flow {
    emit(first)
    breakStream.await()
    throw StreamBroken()
}

private val connected = object : Connectable {
    override suspend fun connect(): String = "CONNECTED"
}

/** Creates the ViewModels through a [ViewModelStore], so each test clears them before Dispatchers.Main is reset. */
private class SwiftFacingHarness {
    private val store = ViewModelStore()

    fun devicesViewModel(devices: Flow<DeviceEvent>): DevicesViewModel {
        val factory = viewModelFactory {
            initializer {
                DevicesViewModel(
                    connect = ConnectUseCase(connected),
                    observeDevices = ObserveDevicesUseCase(
                        object : DeviceSubscribable {
                            override fun subscribeDevices(): Flow<DeviceEvent> = devices
                        }
                    ),
                    mapper = ::fromDomainModel,
                )
            }
        }
        return ViewModelProvider.create(store, factory)[DevicesViewModel::class]
    }

    fun controlViewModel(statusUpdates: Flow<String>): ControlViewModel {
        val factory = viewModelFactory {
            initializer {
                ControlViewModel(
                    deviceId = DEVICE_ID,
                    connect = { ConnectUseCase(connected)(Unit).first() },
                    systemStatusUseCase = RequestSystemStateUseCase(
                        object : SystemStateRequestable {
                            override suspend fun requestSystemState(deviceId: String) = Unit
                        }
                    ),
                    getSystemStatusUseUseCase = GetSystemStatusUseUseCase(
                        object : SystemStateSubscribable {
                            override fun subscribeState(deviceId: String): Flow<String> = statusUpdates
                        }
                    ),
                    executeCommandUseCase = ExecuteWaterCommandUseCase(
                        object : CommandExecutable<String> {
                            override suspend fun execute(i: String) = Unit
                        }
                    ),
                    mapper = DeviceStatusMapper(),
                )
            }
        }
        return ViewModelProvider.create(store, factory)[ControlViewModel::class]
    }

    fun clear() = store.clear()
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun runSwiftFacingTest(block: suspend TestScope.(SwiftFacingHarness) -> Unit) {
    val dispatcher = StandardTestDispatcher()
    Dispatchers.setMain(dispatcher)
    try {
        runTest(dispatcher) {
            val harness = SwiftFacingHarness()
            try {
                block(harness)
            } finally {
                harness.clear()
            }
        }
    } finally {
        Dispatchers.resetMain()
    }
}

/**
 * STORY-10, AC49: a Flow that Swift collects must never throw; an upstream exception ends as an Error UI state.
 * Swift collects `DevicesViewModel.devices` and `ControlViewModel.controlUiState`. Failures before the first value
 * (connect fails, discovery subscription fails at once) are covered by Story2DevicesViewModelTest and
 * ControlViewModelConnectionTest; these tests cover a stream that breaks after it has already shown data, and a
 * status payload that can't be decoded. Turbine fails the test if the StateFlow itself throws.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Story10FlowErrorStateTest : FunSpec({

    // GIVEN discovery has shown a device and the MQTT stream then throws
    // WHEN Swift keeps collecting DevicesViewModel.devices
    // THEN the state becomes Error with that exception, and the flow does not throw
    test("testStory10_devicesStreamBreakingAfterDataShowsErrorState") {
        runSwiftFacingTest { harness ->
            val breakStream = CompletableDeferred<Unit>()
            val target = harness.devicesViewModel(
                streamThatBreaks(DeviceEvent.Info(DEVICE_ID, INFO_PAYLOAD), breakStream)
            )

            target.devices.test {
                awaitItem() shouldBe DevicesListScreenState.Loading
                awaitItem().shouldBeInstanceOf<DevicesListScreenState.Success>()
                breakStream.complete(Unit)
                awaitItem().shouldBeInstanceOf<DevicesListScreenState.Error>().error
                    .shouldBeInstanceOf<StreamBroken>()
                cancelAndIgnoreRemainingEvents()
            }
            target.devices.value.shouldBeInstanceOf<DevicesListScreenState.Error>()
        }
    }

    // GIVEN the control screen has shown a status and the status stream then throws
    // WHEN Swift keeps collecting ControlViewModel.controlUiState
    // THEN the state becomes Error with the exception message, and the flow does not throw
    test("testStory10_controlStatusStreamBreakingAfterDataShowsErrorState") {
        runSwiftFacingTest { harness ->
            val breakStream = CompletableDeferred<Unit>()
            val target = harness.controlViewModel(streamThatBreaks(STATUS_PAYLOAD, breakStream))

            target.controlUiState.test {
                awaitItem() shouldBe ControlUiState.Loading
                awaitItem().shouldBeInstanceOf<ControlUiState.Success>()
                breakStream.complete(Unit)
                awaitItem() shouldBe ControlUiState.Error("MQTT stream broke")
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // GIVEN the device sends a status payload that is not valid JSON
    // WHEN Swift collects ControlViewModel.controlUiState
    // THEN the decoding exception ends as an Error state, not a thrown exception
    test("testStory10_controlMalformedStatusPayloadShowsErrorState") {
        runSwiftFacingTest { harness ->
            val target = harness.controlViewModel(flowOf("not json"))

            target.controlUiState.test {
                awaitItem() shouldBe ControlUiState.Loading
                awaitItem().shouldBeInstanceOf<ControlUiState.Error>()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
})
