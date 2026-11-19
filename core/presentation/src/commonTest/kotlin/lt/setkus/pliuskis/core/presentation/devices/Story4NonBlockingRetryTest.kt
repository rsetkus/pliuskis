package lt.setkus.pliuskis.core.presentation.devices

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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import lt.setkus.pliuskis.core.connect.ConnectUseCase
import lt.setkus.pliuskis.core.connect.Connectable
import lt.setkus.pliuskis.core.devicelist.Device
import lt.setkus.pliuskis.core.devicelist.DeviceDomainModel
import lt.setkus.pliuskis.core.devicelist.DeviceEvent
import lt.setkus.pliuskis.core.devicelist.DeviceSubscribable
import lt.setkus.pliuskis.core.devicelist.DeviceType
import lt.setkus.pliuskis.core.devicelist.ObserveDevicesUseCase
import lt.setkus.pliuskis.core.presentation.devices.DevicesListScreenState.Error
import lt.setkus.pliuskis.core.presentation.devices.DevicesListScreenState.Loading
import lt.setkus.pliuskis.core.presentation.devices.DevicesListScreenState.Success

private const val RETRIES_IN_A_ROW = 3
private const val CONNECTED = "CONNECTED"

private fun mintItem() =
    fromDomainModel(Device(DeviceDomainModel("Mint", "m", DeviceType.PLANT), online = false))

private class MintSubscribable : DeviceSubscribable {
    override fun subscribeDevices(): Flow<DeviceEvent> =
        flowOf(DeviceEvent.Info("m", """{"name":"Mint","deviceId":"m","type":"plant"}"""))
}

/**
 * Suspending connect source. Each call is recorded in [calls]; call N (1-based) waits for the N-th entry of
 * [outcomes] to be completed by the test, so a connect stays running until the test decides. No thread blocks.
 */
private class GatedConnectable(vararg val outcomes: CompletableDeferred<Result<String>>) : Connectable {
    var calls = 0
        private set

    override suspend fun connect(): String {
        calls += 1
        val outcome = outcomes.getOrNull(calls - 1)
        return checkNotNull(outcome) { "unexpected connect call #$calls" }.await().getOrThrow()
    }
}

/** Creates ViewModels through a [ViewModelStore] so each test clears them before Main is reset. */
private class NonBlockingHarness {
    private val store = ViewModelStore()

    fun devicesViewModel(connectable: Connectable): DevicesViewModel {
        val factory = viewModelFactory {
            initializer {
                DevicesViewModel(
                    connect = ConnectUseCase(connectable),
                    observeDevices = ObserveDevicesUseCase(MintSubscribable()),
                    mapper = ::fromDomainModel,
                )
            }
        }
        return ViewModelProvider.create(store, factory)[DevicesViewModel::class]
    }

    fun clear() = store.clear()
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun runNonBlockingTest(block: suspend TestScope.(NonBlockingHarness) -> Unit) {
    val dispatcher = StandardTestDispatcher()
    Dispatchers.setMain(dispatcher)
    try {
        runTest(dispatcher) {
            val harness = NonBlockingHarness()
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
 * STORY-4: Make IotManager non-blocking.
 * Covers AC17 (behaviour): with a suspending connect and no I/O dispatcher, retry() while a connect is running starts
 * no other connect. Everything runs on the test dispatcher; no thread is blocked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Story4NonBlockingRetryTest : FunSpec({

    // AC17
    // GIVEN DevicesViewModel is in Error after a failed connect, and the next connect suspends until released
    // WHEN retry() is called three times in a row while that connect is running
    // THEN exactly one more connect is recorded, the state stays Loading meanwhile,
    // and the devices show once it connects
    test("testStory4_retriesWhileSuspendedConnectRunsRecordOneConnect") {
        runNonBlockingTest { harness ->
            val firstConnect = CompletableDeferred(Result.failure<String>(IllegalStateException("offline")))
            val secondConnect = CompletableDeferred<Result<String>>()
            val connectable = GatedConnectable(firstConnect, secondConnect)
            val target = harness.devicesViewModel(connectable)

            target.devices.test {
                awaitItem() shouldBe Loading
                awaitItem().shouldBeInstanceOf<Error>()

                target.retry()
                testScheduler.runCurrent()
                awaitItem() shouldBe Loading
                repeat(RETRIES_IN_A_ROW) {
                    target.retry()
                    testScheduler.runCurrent()
                }

                connectable.calls shouldBe 2
                expectNoEvents()

                secondConnect.complete(Result.success(CONNECTED))
                awaitItem() shouldBe Success(listOf(mintItem()))
                connectable.calls shouldBe 2
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // AC17
    // GIVEN the first connect of DevicesViewModel is still running (suspended)
    // WHEN retry() is called
    // THEN no other connect starts, and the screen shows the devices once that first connect succeeds
    test("testStory4_retryDuringFirstConnectStartsNoOtherConnect") {
        runNonBlockingTest { harness ->
            val firstConnect = CompletableDeferred<Result<String>>()
            val connectable = GatedConnectable(firstConnect)
            val target = harness.devicesViewModel(connectable)

            target.devices.test {
                awaitItem() shouldBe Loading
                testScheduler.runCurrent()

                repeat(RETRIES_IN_A_ROW) {
                    target.retry()
                    testScheduler.runCurrent()
                }
                connectable.calls shouldBe 1
                expectNoEvents()

                firstConnect.complete(Result.success(CONNECTED))
                awaitItem() shouldBe Success(listOf(mintItem()))
                connectable.calls shouldBe 1
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
})
