package lt.setkus.pliuskis.feature.devices

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import lt.setkus.pliuskis.core.connect.Connectable
import lt.setkus.pliuskis.core.connect.ConnectUseCase
import lt.setkus.pliuskis.core.devicelist.Device
import lt.setkus.pliuskis.core.devicelist.DeviceDomainModel
import lt.setkus.pliuskis.core.devicelist.DeviceEvent
import lt.setkus.pliuskis.core.devicelist.DeviceSubscribable
import lt.setkus.pliuskis.core.devicelist.DeviceType
import lt.setkus.pliuskis.core.devicelist.ObserveDevicesUseCase
import lt.setkus.pliuskis.feature.devices.DevicesListScreenState.Error
import lt.setkus.pliuskis.feature.devices.DevicesListScreenState.Loading
import lt.setkus.pliuskis.feature.devices.DevicesListScreenState.Success
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val RETRY_IO_THREAD_NAME = "story4-io"
private const val RETRY_IO_SHUTDOWN_TIMEOUT_SECONDS = 5L
private const val RETRIES_IN_A_ROW = 3

private fun basilInfoEvent() =
    DeviceEvent.Info("a", """{"name":"Basil","deviceId":"a","type":"plant"}""")

private fun basilListItem() =
    fromDomainModel(Device(DeviceDomainModel("Basil", "a", DeviceType.PLANT), online = false))

/**
 * Connect source whose first call fails; later calls run [onLaterConnect]. [calls] counts every
 * connect, so a test can tell how many connects actually happened.
 */
private class FailOnceConnectable(private val onLaterConnect: () -> String = { "CONNECTED" }) : Connectable {
    val calls = AtomicInteger(0)

    override fun connect(): String {
        val call = calls.incrementAndGet()
        if (call == 1) throw IOException("offline")
        return onLaterConnect()
    }
}

private class RetrySubscribable(private val events: Flow<DeviceEvent>) : DeviceSubscribable {
    override fun subscribeDevices(): Flow<DeviceEvent> = events
}

/** Creates ViewModels through a [ViewModelStore] so each test clears them before Main is reset. */
private class RetryHarness(val dispatcher: TestDispatcher) {
    private val store = ViewModelStore()
    private var created = 0

    fun devicesViewModel(connectable: Connectable, ioDispatcher: CoroutineDispatcher): DevicesViewModel {
        val factory = viewModelFactory {
            initializer {
                DevicesViewModel(
                    connect = ConnectUseCase(connectable),
                    observeDevices = ObserveDevicesUseCase(RetrySubscribable(flowOf(basilInfoEvent()))),
                    mapper = ::fromDomainModel,
                    ioDispatcher = ioDispatcher,
                )
            }
        }
        created += 1
        return ViewModelProvider(store, factory)["retryViewModel$created", DevicesViewModel::class.java]
    }

    fun clear() = store.clear()
}

private fun retryIoExecutor(): ExecutorService =
    Executors.newSingleThreadExecutor { Thread(it, RETRY_IO_THREAD_NAME) }

/** Lets queued and blocked I/O work finish while Dispatchers.Main is still the test dispatcher. */
private fun ExecutorService.drain() {
    shutdown()
    awaitTermination(RETRY_IO_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS).shouldBeTrue()
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun runRetryTest(block: suspend TestScope.(RetryHarness) -> Unit) {
    val dispatcher = StandardTestDispatcher()
    Dispatchers.setMain(dispatcher)
    try {
        runTest(dispatcher) {
            val harness = RetryHarness(dispatcher)
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
 * STORY-4: Retry from the device list error screen.
 * Covers AC17 and AC19 at the ViewModel; AC18 (the tap) is an instrumented test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Story4DevicesViewModelRetryTest : FunSpec({

    // AC17
    // GIVEN DevicesViewModel is in Error because connect failed, and connect will succeed on the next call
    // WHEN retry is triggered
    // THEN the state should go Loading and then Success once the observe-devices use case emits devices
    test("testStory4_retryAfterFailedConnectShowsLoadingThenDevices") {
        runRetryTest { harness ->
            val target = harness.devicesViewModel(FailOnceConnectable(), harness.dispatcher)

            target.devices.test {
                awaitItem() shouldBe Loading
                awaitItem().shouldBeInstanceOf<Error>()

                target.retry()

                awaitItem() shouldBe Loading
                awaitItem() shouldBe Success(listOf(basilListItem()))
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // AC19
    // GIVEN DevicesViewModel is in Error and connect is slow
    // WHEN retry is triggered three times in a row before connect completes
    // THEN connect should be invoked only once for those retries
    test("testStory4_repeatedRetriesWhileConnectingConnectOnce") {
        runRetryTest { harness ->
            val connectGate = CountDownLatch(1)
            val io = retryIoExecutor()
            try {
                val connectable = FailOnceConnectable {
                    connectGate.await()
                    "CONNECTED"
                }
                val target = harness.devicesViewModel(connectable, io.asCoroutineDispatcher())

                target.devices.test {
                    awaitItem() shouldBe Loading
                    awaitItem().shouldBeInstanceOf<Error>()

                    repeat(RETRIES_IN_A_ROW) { target.retry() }
                    testScheduler.runCurrent()
                    connectGate.countDown()

                    // Skip any repeated Loading; the retry must end in the device list.
                    var state = awaitItem()
                    while (state == Loading) state = awaitItem()
                    state shouldBe Success(listOf(basilListItem()))
                    cancelAndIgnoreRemainingEvents()
                }

                io.drain()
                // One failed initial connect plus exactly one connect for the three retries.
                connectable.calls.get() shouldBe 2
            } finally {
                connectGate.countDown()
                if (!io.isShutdown) io.drain()
            }
        }
    }
})
