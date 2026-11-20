package lt.setkus.pliuskis.core.presentation.control

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import lt.setkus.pliuskis.core.command.CommandExecutable
import lt.setkus.pliuskis.core.command.water.ExecuteWaterCommandUseCase
import lt.setkus.pliuskis.core.connect.ConnectUseCase
import lt.setkus.pliuskis.core.connect.Connectable
import lt.setkus.pliuskis.core.connect.ObserveReconnectsUseCase
import lt.setkus.pliuskis.core.connect.Reconnectable
import lt.setkus.pliuskis.core.systemstate.GetSystemStatusUseUseCase
import lt.setkus.pliuskis.core.systemstate.RequestSystemStateUseCase
import lt.setkus.pliuskis.core.systemstate.SystemStateRequestable
import lt.setkus.pliuskis.core.systemstate.SystemStateSubscribable
import kotlin.time.Instant

private const val DEVICE_ID = "pico-1"

// 2026-11-19T18:02:48Z
private const val READ_AT_EPOCH_SECONDS = 1_795_111_368L
private const val STATUS_PAYLOAD =
    """{"humidity":3.0,"waterLevel":70.0,"timestamp":{"epochSeconds":$READ_AT_EPOCH_SECONDS,"nanosecondsOfSecond":0}}"""

/** Records the device id of every status request, status subscription and water command. */
private class DeviceCalls {
    val requested = mutableListOf<String>()
    val subscribed = mutableListOf<String>()
    val watered = mutableListOf<String>()

    fun connectable() = object : Connectable {
        override suspend fun connect(): String = "CONNECTED"
    }

    fun requestable() = object : SystemStateRequestable {
        override suspend fun requestSystemState(deviceId: String) {
            requested += deviceId
        }
    }

    fun subscribable() = object : SystemStateSubscribable {
        override fun subscribeState(deviceId: String): Flow<String> = flow {
            subscribed += deviceId
            emit(STATUS_PAYLOAD)
            awaitCancellation()
        }
    }

    fun waterCommand() = object : CommandExecutable<String> {
        override suspend fun execute(i: String) {
            watered += i
        }
    }
}

/** Creates the ViewModel through a [ViewModelStore] so each test clears it before Main is reset. */
private class ControlStore {
    private val store = ViewModelStore()

    fun controlViewModel(calls: DeviceCalls): ControlViewModel {
        val factory = viewModelFactory {
            initializer {
                ControlViewModel(
                    deviceId = DEVICE_ID,
                    connect = { ConnectUseCase(calls.connectable())(Unit).first() },
                    systemStatusUseCase = RequestSystemStateUseCase(calls.requestable()),
                    getSystemStatusUseUseCase = GetSystemStatusUseUseCase(calls.subscribable()),
                    executeCommandUseCase = ExecuteWaterCommandUseCase(calls.waterCommand()),
                    observeReconnects = ObserveReconnectsUseCase(
                        object : Reconnectable {
                            override fun reconnects(): Flow<Unit> = emptyFlow()
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
private fun runControlTest(block: suspend TestScope.(ControlStore) -> Unit) {
    val dispatcher = StandardTestDispatcher()
    Dispatchers.setMain(dispatcher)
    try {
        runTest(dispatcher) {
            val store = ControlStore()
            try {
                block(store)
            } finally {
                store.clear()
            }
        }
    } finally {
        Dispatchers.resetMain()
    }
}

/**
 * STORY-9: ControlViewModel is shared and gets the device id straight from its caller (no SavedStateHandle).
 * Covers AC42 and, as a commonTest ViewModel test with the kotlinx-coroutines-test Main dispatcher, AC45.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Story9ControlViewModelDeviceIdTest : FunSpec({

    // AC42
    // GIVEN ControlViewModel constructed with deviceId = "pico-1" and a client that connects
    // WHEN its state is collected
    // THEN it requests status for "pico-1", subscribes to "pico-1", and shows that device's status
    test("testStory9_controlViewModelRequestsAndSubscribesForGivenDeviceId") {
        runControlTest { store ->
            val calls = DeviceCalls()
            val target = store.controlViewModel(calls)

            target.controlUiState.test {
                awaitItem() shouldBe ControlUiState.Loading
                awaitItem() shouldBe ControlUiState.Success(
                    DeviceStatus(
                        humidity = HumidityStatus.THIRSTY,
                        waterLevel = WaterLevelStatus.ENOUGH,
                        updatedAt = Instant.fromEpochSeconds(READ_AT_EPOCH_SECONDS),
                    )
                )
                testScheduler.runCurrent()

                calls.requested shouldBe listOf(DEVICE_ID)
                calls.subscribed shouldBe listOf(DEVICE_ID)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // AC42
    // GIVEN ControlViewModel constructed with deviceId = "pico-1"
    // WHEN the user waters the plant
    // THEN the water command goes to "pico-1"
    test("testStory9_waterPlantSendsCommandToGivenDeviceId") {
        runControlTest { store ->
            val calls = DeviceCalls()
            val target = store.controlViewModel(calls)

            target.waterPlant()
            testScheduler.runCurrent()

            calls.watered shouldBe listOf(DEVICE_ID)
        }
    }
})
