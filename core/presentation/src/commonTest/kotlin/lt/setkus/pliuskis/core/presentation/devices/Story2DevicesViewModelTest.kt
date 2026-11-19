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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
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

private const val EMPTY_LIST_TIMEOUT_MILLIS = 3_000L

private fun infoJson(deviceId: String, name: String) =
    """{"name":"$name","deviceId":"$deviceId","type":"plant"}"""

private fun basilItem() =
    fromDomainModel(Device(DeviceDomainModel("Basil", "a", DeviceType.PLANT), online = false))

/** Connect source that records that it ran; [onConnect] decides the outcome. */
private class FakeConnectable(private val onConnect: suspend () -> String = { "CONNECTED" }) : Connectable {
    val connected = CompletableDeferred<Unit>()

    override suspend fun connect(): String {
        connected.complete(Unit)
        return onConnect()
    }
}

/** Discovery source whose [subscribed] flag flips when the MQTT subscription is collected. */
private class FakeDeviceSubscribable(private val events: Flow<DeviceEvent>) : DeviceSubscribable {
    var subscribed = false

    override fun subscribeDevices(): Flow<DeviceEvent> = events.onStart { subscribed = true }
}

/**
 * Creates ViewModels through a [ViewModelStore] so each test can clear them, which cancels their
 * viewModelScope before Dispatchers.Main is reset.
 */
private class ViewModelHarness(val dispatcher: TestDispatcher) {
    private val store = ViewModelStore()
    private var created = 0

    fun devicesViewModel(
        connectable: Connectable,
        subscribable: DeviceSubscribable,
    ): DevicesViewModel {
        val factory = viewModelFactory {
            initializer {
                DevicesViewModel(
                    connect = ConnectUseCase(connectable),
                    observeDevices = ObserveDevicesUseCase(subscribable),
                    mapper = ::fromDomainModel,
                )
            }
        }
        created += 1
        return ViewModelProvider.create(store, factory)["devicesViewModel$created", DevicesViewModel::class]
    }

    fun clear() = store.clear()
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun runViewModelTest(block: suspend TestScope.(ViewModelHarness) -> Unit) {
    val dispatcher = StandardTestDispatcher()
    Dispatchers.setMain(dispatcher)
    try {
        runTest(dispatcher) {
            val harness = ViewModelHarness(dispatcher)
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
 * STORY-2: DevicesViewModel connects then discovers; app launch no longer connects.
 * Covers AC4, AC5, AC6, AC9, AC10. AC8 needs the HiveMQ client, so it is a JVM test in feature/devices
 * (`Story2AlreadyConnectedDevicesViewModelTest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Story2DevicesViewModelTest : FunSpec({

    // AC4
    // GIVEN connect succeeds and the observe-devices use case then emits a list of devices
    // WHEN DevicesViewModel state is collected
    // THEN should emit Loading followed by Success containing those devices mapped to list items
    test("testStory2_connectSucceedsThenDevicesShowAsSuccess") {
        runViewModelTest { harness ->
            val subscribable = FakeDeviceSubscribable(flowOf(DeviceEvent.Info("a", infoJson("a", "Basil"))))
            val target = harness.devicesViewModel(FakeConnectable(), subscribable)

            target.devices.test {
                awaitItem() shouldBe Loading
                awaitItem() shouldBe Success(listOf(basilItem()))
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // AC5
    // GIVEN connect has not completed yet
    // WHEN DevicesViewModel state is collected
    // THEN the state should stay Loading and discovery should not be subscribed
    test("testStory2_pendingConnectKeepsLoadingWithoutSubscribing") {
        runViewModelTest { harness ->
            val connectGate = CompletableDeferred<Unit>()
            try {
                val connectable = FakeConnectable {
                    connectGate.await()
                    "CONNECTED"
                }
                val subscribable = FakeDeviceSubscribable(flowOf(DeviceEvent.Info("a", infoJson("a", "Basil"))))
                val target = harness.devicesViewModel(connectable, subscribable)

                target.devices.test {
                    awaitItem() shouldBe Loading
                    connectable.connected.await()
                    testScheduler.advanceTimeBy(EMPTY_LIST_TIMEOUT_MILLIS * 3)
                    testScheduler.runCurrent()
                    expectNoEvents()
                    subscribable.subscribed shouldBe false
                    cancelAndIgnoreRemainingEvents()
                }
            } finally {
                connectGate.complete(Unit)
            }
        }
    }

    // AC6
    // GIVEN connect fails with an exception
    // WHEN DevicesViewModel state is collected
    // THEN should emit Loading followed by Error, and discovery should never be subscribed
    test("testStory2_connectFailureShowsErrorWithoutSubscribing") {
        runViewModelTest { harness ->
            val connectable = FakeConnectable { throw IllegalStateException("offline") }
            val subscribable = FakeDeviceSubscribable(flowOf(DeviceEvent.Info("a", infoJson("a", "Basil"))))
            val target = harness.devicesViewModel(connectable, subscribable)

            target.devices.test {
                awaitItem() shouldBe Loading
                awaitItem().shouldBeInstanceOf<Error>()
                testScheduler.advanceTimeBy(EMPTY_LIST_TIMEOUT_MILLIS * 3)
                testScheduler.runCurrent()
                expectNoEvents()
                subscribable.subscribed shouldBe false
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // AC9
    // GIVEN connect succeeds and the observe-devices use case emits a Left
    // WHEN DevicesViewModel state is collected
    // THEN should emit Error
    test("testStory2_subscriptionFailureShowsError") {
        runViewModelTest { harness ->
            val subscribable = FakeDeviceSubscribable(flow { throw IllegalStateException("subscribe failed") })
            val target = harness.devicesViewModel(FakeConnectable(), subscribable)

            target.devices.test {
                awaitItem() shouldBe Loading
                awaitItem().shouldBeInstanceOf<Error>()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // AC10
    // GIVEN connect succeeds and the observe-devices use case emits nothing for 3 s
    // WHEN DevicesViewModel state is collected
    // THEN should emit Success with an empty list, not Error
    test("testStory2_noDevicesWithinTimeoutShowsEmptyList") {
        runViewModelTest { harness ->
            val subscribable = FakeDeviceSubscribable(flow { awaitCancellation() })
            val target = harness.devicesViewModel(FakeConnectable(), subscribable)

            target.devices.test {
                awaitItem() shouldBe Loading
                testScheduler.advanceTimeBy(EMPTY_LIST_TIMEOUT_MILLIS - 1)
                testScheduler.runCurrent()
                expectNoEvents()
                testScheduler.advanceTimeBy(2)
                testScheduler.runCurrent()
                awaitItem() shouldBe Success(emptyList())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
})
