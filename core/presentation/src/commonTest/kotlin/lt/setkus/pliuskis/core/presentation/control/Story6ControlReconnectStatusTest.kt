package lt.setkus.pliuskis.core.presentation.control

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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

private const val DEVICE_ID = "b1"

/** Longer than any wait in the control screen, so a late or repeated request would show up. */
private const val SETTLE_MILLIS = 60_000L

/**
 * The MQTT client as the control screen sees it through the domain ports (the shared `IotManager` behind
 * `ConnectMqttSource`, `AwsSystemStateRequest`, `SubscribeSystemStatusTopic` and `MqttExecuteWaterCommand`). It
 * records every status request and water command, and lets a test play a connection loss followed by the client's own
 * reconnect: a new CONNACK, which `reconnects()` reports.
 */
private class FakeMqttClient :
    Connectable,
    Reconnectable,
    SystemStateRequestable,
    SystemStateSubscribable,
    CommandExecutable<String> {

    val statusRequests = mutableListOf<String>()
    val waterCommands = mutableListOf<String>()

    private val reconnectEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 16)

    override suspend fun connect(): String = "CONNECTED"

    override fun reconnects(): Flow<Unit> = reconnectEvents

    override suspend fun requestSystemState(deviceId: String) {
        statusRequests += deviceId
    }

    override fun subscribeState(deviceId: String): Flow<String> = flow { awaitCancellation() }

    override suspend fun execute(i: String) {
        waterCommands += i
    }

    /** The connection dropped and the client reconnected on its own: the broker sent a new CONNACK. */
    fun loseConnectionAndReconnect() {
        check(reconnectEvents.tryEmit(Unit)) { "reconnect event was not delivered" }
    }
}

private class ReconnectHarness {
    private val store = ViewModelStore()

    fun controlViewModel(client: FakeMqttClient): ControlViewModel {
        val factory = viewModelFactory {
            initializer {
                newControlViewModel(
                    deviceId = DEVICE_ID,
                    connect = { ConnectUseCase(client)(Unit).first() },
                    systemStatusUseCase = RequestSystemStateUseCase(client),
                    getSystemStatusUseUseCase = GetSystemStatusUseUseCase(client),
                    executeCommandUseCase = ExecuteWaterCommandUseCase(client),
                    observeReconnects = ObserveReconnectsUseCase(client),
                    mapper = DeviceStatusMapper(),
                )
            }
        }
        return ViewModelProvider.create(store, factory)[ControlViewModel::class]
    }

    fun clear() = store.clear()
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun runReconnectTest(block: suspend TestScope.(ReconnectHarness) -> Unit) {
    val dispatcher = StandardTestDispatcher()
    Dispatchers.setMain(dispatcher)
    try {
        runTest(dispatcher) {
            val harness = ReconnectHarness()
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
 * STORY-6 AC44: status is not retained (a device answers only a request), so after the MQTT client loses its
 * connection and reconnects on its own, the open control screen asks again: exactly one new status request per
 * CONNACK, with no foreground return and no retry. A reconnect never sends a water command. ControlViewModel and the
 * client contract are shared, so this holds on Android and iOS; the client reports each reconnect through the domain
 * port [Reconnectable] (`IotManager.reconnects()` behind it).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Story6ControlReconnectStatusTest : FunSpec({

    // AC44
    // GIVEN the control screen of b1 is open, connected, and has requested b1's status once
    // WHEN the client loses the connection and reconnects on its own (a new CONNACK)
    // THEN exactly one new status request for b1 is published after that CONNACK, and no more follow
    test("testStory6_reconnectPublishesExactlyOneNewStatusRequestForTheOpenDevice") {
        runReconnectTest { harness ->
            val client = FakeMqttClient()
            val target = harness.controlViewModel(client)
            backgroundScope.launch { target.controlUiState.collect {} }
            runCurrent()
            client.statusRequests shouldBe listOf(DEVICE_ID)

            client.loseConnectionAndReconnect()
            runCurrent()
            advanceTimeBy(SETTLE_MILLIS)
            runCurrent()

            client.statusRequests shouldBe listOf(DEVICE_ID, DEVICE_ID)
        }
    }

    // AC44
    // GIVEN the control screen of b1 is open and connected
    // WHEN the client reconnects on its own twice
    // THEN each CONNACK brings exactly one new status request for b1
    test("testStory6_everyReconnectPublishesOneStatusRequest") {
        runReconnectTest { harness ->
            val client = FakeMqttClient()
            val target = harness.controlViewModel(client)
            backgroundScope.launch { target.controlUiState.collect {} }
            runCurrent()

            client.loseConnectionAndReconnect()
            runCurrent()
            client.statusRequests.size shouldBe 2
            client.loseConnectionAndReconnect()
            runCurrent()
            advanceTimeBy(SETTLE_MILLIS)
            runCurrent()

            client.statusRequests shouldBe listOf(DEVICE_ID, DEVICE_ID, DEVICE_ID)
        }
    }

    // AC44
    // GIVEN the user tapped water on b1 once while connected
    // WHEN the client loses the connection and reconnects on its own, twice
    // THEN the only water command is the one the user tapped
    test("testStory6_reconnectNeverPublishesAWaterCommand") {
        runReconnectTest { harness ->
            val client = FakeMqttClient()
            val target = harness.controlViewModel(client)
            backgroundScope.launch { target.controlUiState.collect {} }
            runCurrent()
            target.waterPlant()
            runCurrent()
            client.waterCommands shouldBe listOf(DEVICE_ID)

            client.loseConnectionAndReconnect()
            runCurrent()
            client.loseConnectionAndReconnect()
            runCurrent()
            advanceTimeBy(SETTLE_MILLIS)
            runCurrent()

            client.waterCommands shouldBe listOf(DEVICE_ID)
        }
    }
})
