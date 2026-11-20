package lt.setkus.pliuskis.core.presentation.control

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
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

private const val DEVICE_ID = "a"
private const val CONNECT = "connect"
private const val REQUEST = "request status"
private const val SUBSCRIBE = "subscribe status"

/** Records every MQTT operation the control screen performs, in order. */
private class MqttLog {
    val events = mutableListOf<String>()

    fun connectable(onConnect: () -> String) = object : Connectable {
        override suspend fun connect(): String {
            events += CONNECT
            return onConnect()
        }
    }

    fun requestable() = object : SystemStateRequestable {
        override suspend fun requestSystemState(deviceId: String) {
            events += REQUEST
        }
    }

    fun subscribable() = object : SystemStateSubscribable {
        override fun subscribeState(deviceId: String): Flow<String> =
            flow<String> { awaitCancellation() }.onStart { events += SUBSCRIBE }
    }
}

private class ControlHarness {
    private val store = ViewModelStore()

    fun controlViewModel(log: MqttLog, onConnect: () -> String): ControlViewModel {
        val factory = viewModelFactory {
            initializer {
                ControlViewModel(
                    deviceId = DEVICE_ID,
                    connect = { ConnectUseCase(log.connectable(onConnect))(Unit).first() },
                    systemStatusUseCase = RequestSystemStateUseCase(log.requestable()),
                    getSystemStatusUseUseCase = GetSystemStatusUseUseCase(log.subscribable()),
                    executeCommandUseCase = ExecuteWaterCommandUseCase(
                        object : CommandExecutable<String> {
                            override suspend fun execute(i: String) = Unit
                        }
                    ),
                    observeReconnects = ObserveReconnectsUseCase(
                        object : Reconnectable {
                            override fun reconnects(): Flow<Unit> = emptyFlow()
                        }
                    ),
                    mapper = DeviceStatusMapper()
                )
            }
        }
        return ViewModelProvider.create(store, factory)[ControlViewModel::class]
    }

    fun clear() = store.clear()
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun runControlTest(block: suspend TestScope.(ControlHarness) -> Unit) {
    val dispatcher = StandardTestDispatcher()
    Dispatchers.setMain(dispatcher)
    try {
        runTest(dispatcher) {
            val harness = ControlHarness()
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
 * After process death the control screen is restored without the device list, so the MQTT client
 * is not connected. The control screen must connect before it requests or subscribes to status.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ControlViewModelConnectionTest : FunSpec({

    test("GIVEN a restored control screen and a disconnected client WHEN it starts THEN connects before status calls") {
        runControlTest { harness ->
            val log = MqttLog()
            val target = harness.controlViewModel(log) { "CONNECTED" }

            target.controlUiState.test {
                awaitItem() shouldBe ControlUiState.Loading
                testScheduler.runCurrent()

                log.events.first() shouldBe CONNECT
                log.events shouldContainExactlyInAnyOrder listOf(CONNECT, REQUEST, SUBSCRIBE)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    test("GIVEN the connection fails WHEN the control screen starts THEN shows an error without status calls") {
        runControlTest { harness ->
            val log = MqttLog()
            val target = harness.controlViewModel(log) { throw IllegalStateException("offline") }

            target.controlUiState.test {
                awaitItem() shouldBe ControlUiState.Loading
                awaitItem().shouldBeInstanceOf<ControlUiState.Error>()
                testScheduler.runCurrent()

                log.events shouldContainOnly listOf(CONNECT)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
})
