package lt.setkus.pliuskis.feature.devices

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.exceptions.MqttClientStateException
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
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
import lt.setkus.pliuskis.core.connect.Connectable
import lt.setkus.pliuskis.core.connect.ConnectUseCase
import lt.setkus.pliuskis.core.devicelist.Device
import lt.setkus.pliuskis.core.devicelist.DeviceDomainModel
import lt.setkus.pliuskis.core.devicelist.DeviceEvent
import lt.setkus.pliuskis.core.devicelist.DeviceSubscribable
import lt.setkus.pliuskis.core.devicelist.DeviceType
import lt.setkus.pliuskis.core.devicelist.ObserveDevicesUseCase
import lt.setkus.pliuskis.data.ConnectMqttSource
import lt.setkus.pliuskis.data.mqttclient.HiveMqttClientManager
import lt.setkus.pliuskis.feature.devices.DevicesListScreenState.Error
import lt.setkus.pliuskis.feature.devices.DevicesListScreenState.Loading
import lt.setkus.pliuskis.feature.devices.DevicesListScreenState.Success
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val IO_THREAD_NAME = "story2-io"
private const val EMPTY_LIST_TIMEOUT_MILLIS = 3_000L
private const val IO_SHUTDOWN_TIMEOUT_SECONDS = 5L

private fun infoJson(deviceId: String, name: String) =
    """{"name":"$name","deviceId":"$deviceId","type":"plant"}"""

private fun basilItem() =
    fromDomainModel(Device(DeviceDomainModel("Basil", "a", DeviceType.PLANT), online = false))

/** Connect source that records the thread it ran on; [onConnect] decides the outcome. */
private class FakeConnectable(private val onConnect: () -> String = { "CONNECTED" }) : Connectable {
    val connected = CompletableDeferred<Thread>()

    override fun connect(): String {
        connected.complete(Thread.currentThread())
        return onConnect()
    }
}

/** Discovery source whose [subscribed] flag flips when the MQTT subscription is collected. */
private class FakeDeviceSubscribable(private val events: Flow<DeviceEvent>) : DeviceSubscribable {
    @Volatile
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
        ioDispatcher: CoroutineDispatcher,
    ): DevicesViewModel {
        val factory = viewModelFactory {
            initializer {
                DevicesViewModel(
                    connect = ConnectUseCase(connectable),
                    observeDevices = ObserveDevicesUseCase(subscribable),
                    mapper = ::fromDomainModel,
                    ioDispatcher = ioDispatcher,
                )
            }
        }
        created += 1
        return ViewModelProvider(store, factory)["devicesViewModel$created", DevicesViewModel::class.java]
    }

    fun clear() = store.clear()
}

/** HiveMQ client that is already connected: the blocking connect throws, as the real client does. */
private fun alreadyConnectedClient(): Mqtt3Client {
    val blocking = mockk<Mqtt3BlockingClient>(relaxed = true)
    every { blocking.connect() } throws MqttClientStateException("MQTT client is already connected or connecting.")

    val config = mockk<Mqtt3ClientConfig>(relaxed = true)
    every { config.state } returns MqttClientState.CONNECTED

    val client = mockk<Mqtt3Client>(relaxed = true)
    every { client.config } returns config
    every { client.state } returns MqttClientState.CONNECTED
    every { client.toBlocking() } returns blocking
    return client
}

private fun ioExecutor(): ExecutorService = Executors.newSingleThreadExecutor { Thread(it, IO_THREAD_NAME) }

/** Lets blocked I/O work finish while Dispatchers.Main is still the test dispatcher. */
private fun ExecutorService.shutdownAndWait() {
    shutdown()
    awaitTermination(IO_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS).shouldBeTrue()
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
 * Covers AC4, AC5, AC6, AC8, AC9, AC10, AC11.
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
            val target = harness.devicesViewModel(FakeConnectable(), subscribable, harness.dispatcher)

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
            val connectGate = CountDownLatch(1)
            val io = ioExecutor()
            try {
                val connectable = FakeConnectable {
                    connectGate.await()
                    "CONNECTED"
                }
                val subscribable = FakeDeviceSubscribable(flowOf(DeviceEvent.Info("a", infoJson("a", "Basil"))))
                val target = harness.devicesViewModel(connectable, subscribable, io.asCoroutineDispatcher())

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
                connectGate.countDown()
                io.shutdownAndWait()
            }
        }
    }

    // AC6
    // GIVEN connect fails with an exception
    // WHEN DevicesViewModel state is collected
    // THEN should emit Loading followed by Error, and discovery should never be subscribed
    test("testStory2_connectFailureShowsErrorWithoutSubscribing") {
        runViewModelTest { harness ->
            val connectable = FakeConnectable { throw IOException("offline") }
            val subscribable = FakeDeviceSubscribable(flowOf(DeviceEvent.Info("a", infoJson("a", "Basil"))))
            val target = harness.devicesViewModel(connectable, subscribable, harness.dispatcher)

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

    // AC8
    // GIVEN the MQTT client is already connected (HiveMQ throws MqttClientStateException on connect)
    // WHEN DevicesViewModel state is collected and the use case then emits devices
    // THEN should emit Success with those devices, not Error
    test("testStory2_alreadyConnectedClientStillShowsDevices") {
        runViewModelTest { harness ->
            val connectable = ConnectMqttSource(HiveMqttClientManager(alreadyConnectedClient()))
            val subscribable = FakeDeviceSubscribable(flowOf(DeviceEvent.Info("a", infoJson("a", "Basil"))))
            val target = harness.devicesViewModel(connectable, subscribable, harness.dispatcher)

            target.devices.test {
                awaitItem() shouldBe Loading
                awaitItem() shouldBe Success(listOf(basilItem()))
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
            val target = harness.devicesViewModel(FakeConnectable(), subscribable, harness.dispatcher)

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
            val target = harness.devicesViewModel(FakeConnectable(), subscribable, harness.dispatcher)

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

    // AC11
    // GIVEN a DevicesViewModel built with an injected background dispatcher
    // WHEN it connects
    // THEN the connect call should run on that dispatcher's thread and not on Dispatchers.Main
    test("testStory2_connectRunsOnInjectedBackgroundDispatcher") {
        runViewModelTest { harness ->
            val io = ioExecutor()
            try {
                val ioThread = io.submit(Callable { Thread.currentThread() }).get()
                val connectable = FakeConnectable()
                val subscribable = FakeDeviceSubscribable(flow { awaitCancellation() })
                val target = harness.devicesViewModel(connectable, subscribable, io.asCoroutineDispatcher())

                target.devices.test {
                    awaitItem() shouldBe Loading
                    connectable.connected.await() shouldBeSameInstanceAs ioThread
                    cancelAndIgnoreRemainingEvents()
                }
            } finally {
                io.shutdownAndWait()
            }
        }
    }
})
