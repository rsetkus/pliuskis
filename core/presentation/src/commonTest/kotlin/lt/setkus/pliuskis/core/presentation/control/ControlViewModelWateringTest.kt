package lt.setkus.pliuskis.core.presentation.control

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import lt.setkus.pliuskis.core.command.CommandExecutable
import lt.setkus.pliuskis.core.command.water.ExecuteWaterCommandUseCase
import lt.setkus.pliuskis.core.connect.ObserveReconnectsUseCase
import lt.setkus.pliuskis.core.connect.Reconnectable
import lt.setkus.pliuskis.core.systemstate.GetSystemStatusUseUseCase
import lt.setkus.pliuskis.core.systemstate.RequestSystemStateUseCase
import lt.setkus.pliuskis.core.systemstate.SystemStateRequestable
import lt.setkus.pliuskis.core.systemstate.SystemStateSubscribable

private const val DEVICE_ID = "pico-1"
private const val CONFIRMATION_MILLIS = 3_000L

/** A water command the test completes by hand; counts how often it was sent. */
private class GatedWaterCommand : CommandExecutable<String> {
    var calls = 0
        private set
    var outcome = CompletableDeferred<Unit>()

    override suspend fun execute(i: String) {
        calls++
        outcome.await()
    }
}

/** Connect results the test hands out in order; counts the attempts. */
private class ScriptedConnect(private vararg val results: Either<Throwable, String>) {
    var attempts = 0
        private set

    suspend operator fun invoke(): Either<Throwable, String> = results[minOf(attempts++, results.lastIndex)]
}

private class Requests : SystemStateRequestable {
    val devices = mutableListOf<String>()
    override suspend fun requestSystemState(deviceId: String) {
        devices += deviceId
    }
}

private fun controlViewModel(
    store: ViewModelStore,
    water: GatedWaterCommand = GatedWaterCommand(),
    connect: ScriptedConnect = ScriptedConnect("CONNECTED".right()),
    requests: Requests = Requests(),
): ControlViewModel {
    val factory = viewModelFactory {
        initializer {
            ControlViewModel(
                deviceId = DEVICE_ID,
                connect = { connect() },
                systemStatusUseCase = RequestSystemStateUseCase(requests),
                getSystemStatusUseUseCase = GetSystemStatusUseUseCase(
                    object : SystemStateSubscribable {
                        override fun subscribeState(deviceId: String): Flow<String> = flow { awaitCancellation() }
                    }
                ),
                executeCommandUseCase = ExecuteWaterCommandUseCase(water),
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

@OptIn(ExperimentalCoroutinesApi::class)
private fun runViewModelTest(block: suspend TestScope.(ViewModelStore) -> Unit) {
    val dispatcher = StandardTestDispatcher()
    Dispatchers.setMain(dispatcher)
    try {
        runTest(dispatcher) {
            val store = ViewModelStore()
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

/** The water button's feedback and the control screen's Retry. */
@OptIn(ExperimentalCoroutinesApi::class)
class ControlViewModelWateringTest : FunSpec({

    test("GIVEN idle WHEN water is tapped THEN the command is sent and the state is SENDING until it ends") {
        runViewModelTest { store ->
            val water = GatedWaterCommand()
            val target = controlViewModel(store, water)
            target.wateringState.value shouldBe WateringState.IDLE

            target.waterPlant()
            runCurrent()

            target.wateringState.value shouldBe WateringState.SENDING
            water.calls shouldBe 1
        }
    }

    test("GIVEN the command is sent WHEN it succeeds THEN STARTED, and IDLE again after the confirmation") {
        runViewModelTest { store ->
            val water = GatedWaterCommand()
            val target = controlViewModel(store, water)

            target.waterPlant()
            water.outcome.complete(Unit)
            runCurrent()
            target.wateringState.value shouldBe WateringState.STARTED

            advanceTimeBy(CONFIRMATION_MILLIS + 1)
            target.wateringState.value shouldBe WateringState.IDLE
        }
    }

    test("GIVEN the command is sent WHEN it fails THEN FAILED, and it stays there") {
        runViewModelTest { store ->
            val water = GatedWaterCommand()
            val target = controlViewModel(store, water)

            target.waterPlant()
            water.outcome.completeExceptionally(IllegalStateException("not connected"))
            runCurrent()
            advanceTimeBy(CONFIRMATION_MILLIS * 2)

            target.wateringState.value shouldBe WateringState.FAILED
        }
    }

    test("GIVEN a command is still sending WHEN water is tapped again THEN it is not sent twice") {
        runViewModelTest { store ->
            val water = GatedWaterCommand()
            val target = controlViewModel(store, water)

            target.waterPlant()
            runCurrent()
            target.waterPlant()
            runCurrent()

            water.calls shouldBe 1
        }
    }

    test("GIVEN a failed command WHEN water is tapped again THEN it is sent again") {
        runViewModelTest { store ->
            val water = GatedWaterCommand()
            val target = controlViewModel(store, water)
            target.waterPlant()
            water.outcome.completeExceptionally(IllegalStateException("not connected"))
            runCurrent()

            water.outcome = CompletableDeferred()
            target.waterPlant()
            runCurrent()

            target.wateringState.value shouldBe WateringState.SENDING
            water.calls shouldBe 2
        }
    }

    test("GIVEN the connection failed WHEN retry THEN it connects again and asks for the status") {
        runViewModelTest { store ->
            val connect = ScriptedConnect(IllegalStateException("offline").left(), "CONNECTED".right())
            val requests = Requests()
            val target = controlViewModel(store, connect = connect, requests = requests)

            target.controlUiState.test {
                awaitItem() shouldBe ControlUiState.Loading
                awaitItem().shouldBeInstanceOf<ControlUiState.Error>()

                target.retry()

                awaitItem() shouldBe ControlUiState.Loading
                testScheduler.runCurrent()
                connect.attempts shouldBe 2
                requests.devices shouldBe listOf(DEVICE_ID)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    test("GIVEN the screen is loading WHEN retry THEN nothing reconnects") {
        runViewModelTest { store ->
            val connect = ScriptedConnect("CONNECTED".right())
            val target = controlViewModel(store, connect = connect)

            target.controlUiState.test {
                awaitItem() shouldBe ControlUiState.Loading
                target.retry()
                testScheduler.runCurrent()

                connect.attempts shouldBe 1
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
})
