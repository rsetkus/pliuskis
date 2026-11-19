package lt.setkus.pliuskis.feature.devices

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.exceptions.MqttClientStateException
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientConfig
import com.hivemq.client.mqtt.mqtt3.Mqtt3RxClient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
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
import lt.setkus.pliuskis.core.presentation.devices.DevicesListScreenState.Loading
import lt.setkus.pliuskis.core.presentation.devices.DevicesListScreenState.Success
import lt.setkus.pliuskis.core.presentation.devices.DevicesViewModel
import lt.setkus.pliuskis.core.presentation.devices.fromDomainModel
import lt.setkus.pliuskis.data.ConnectMqttSource
import lt.setkus.pliuskis.data.mqttclient.HiveMqttClientManager

private fun infoJson(deviceId: String, name: String) =
    """{"name":"$name","deviceId":"$deviceId","type":"plant"}"""

private fun basilItem() =
    fromDomainModel(Device(DeviceDomainModel("Basil", "a", DeviceType.PLANT), online = false))

/** Discovery source that emits [events] when the MQTT subscription is collected. */
private class FakeDeviceSubscribable(private val events: Flow<DeviceEvent>) : DeviceSubscribable {
    override fun subscribeDevices(): Flow<DeviceEvent> = events
}

/**
 * Creates the ViewModel through a [ViewModelStore] so the test can clear it, which cancels its viewModelScope before
 * Dispatchers.Main is reset.
 */
private class ViewModelHarness {
    private val store = ViewModelStore()

    fun devicesViewModel(connectable: Connectable, subscribable: DeviceSubscribable): DevicesViewModel {
        val factory = viewModelFactory {
            initializer {
                DevicesViewModel(
                    connect = ConnectUseCase(connectable),
                    observeDevices = ObserveDevicesUseCase(subscribable),
                    mapper = ::fromDomainModel,
                )
            }
        }
        return ViewModelProvider.create(store, factory)[DevicesViewModel::class]
    }

    fun clear() = store.clear()
}

/**
 * HiveMQ client that is already connected. As with the real client, a second blocking connect fails with
 * [MqttClientStateException]; here the blocking view itself throws it, so any connect attempt fails the same way.
 */
private class AlreadyConnectedMqtt3Client : Mqtt3Client {
    override fun getState(): MqttClientState = MqttClientState.CONNECTED

    override fun toBlocking(): Mqtt3BlockingClient =
        throw MqttClientStateException("MQTT client is already connected or connecting.")

    override fun getConfig(): Mqtt3ClientConfig = error("config is not used by the connect path")

    override fun toRx(): Mqtt3RxClient = error("the Rx client is not used by the connect path")

    override fun toAsync(): Mqtt3AsyncClient = error("the async client is not used by the connect path")
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun runViewModelTest(block: suspend TestScope.(ViewModelHarness) -> Unit) {
    val dispatcher = StandardTestDispatcher()
    Dispatchers.setMain(dispatcher)
    try {
        runTest(dispatcher) {
            val harness = ViewModelHarness()
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
 * Covers AC8 with the real HiveMQ connect path, so it runs on the JVM only. The other STORY-2 ViewModel tests are
 * shared, in core:presentation's `Story2DevicesViewModelTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Story2AlreadyConnectedDevicesViewModelTest : FunSpec({

    // AC8
    // GIVEN the MQTT client is already connected (HiveMQ throws MqttClientStateException on connect)
    // WHEN DevicesViewModel state is collected and the use case then emits devices
    // THEN should emit Success with those devices, not Error
    test("testStory2_alreadyConnectedClientStillShowsDevices") {
        runViewModelTest { harness ->
            val connectable = ConnectMqttSource(HiveMqttClientManager(AlreadyConnectedMqtt3Client()))
            val subscribable = FakeDeviceSubscribable(flowOf(DeviceEvent.Info("a", infoJson("a", "Basil"))))
            val target = harness.devicesViewModel(connectable, subscribable)

            target.devices.test {
                awaitItem() shouldBe Loading
                awaitItem() shouldBe Success(listOf(basilItem()))
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
})
