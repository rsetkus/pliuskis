package lt.setkus.pliuskis.core.presentation.devices

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import lt.setkus.pliuskis.core.connect.ConnectUseCase
import lt.setkus.pliuskis.core.connect.Connectable
import lt.setkus.pliuskis.core.connect.IotAccessDeniedException
import lt.setkus.pliuskis.core.devicelist.Device
import lt.setkus.pliuskis.core.devicelist.DeviceDomainModel
import lt.setkus.pliuskis.core.devicelist.DeviceEvent
import lt.setkus.pliuskis.core.devicelist.DeviceSubscribable
import lt.setkus.pliuskis.core.devicelist.DeviceType
import lt.setkus.pliuskis.core.devicelist.ObserveDevicesUseCase
import lt.setkus.pliuskis.core.presentation.devices.DevicesListScreenState.Error
import lt.setkus.pliuskis.core.presentation.devices.DevicesListScreenState.Loading
import lt.setkus.pliuskis.core.presentation.devices.DevicesListScreenState.Success

private const val FIVE_MINUTES_MILLIS = 5 * 60 * 1_000L

private fun basilInfoEvent() = DeviceEvent.Info("a", """{"name":"Basil","deviceId":"a","type":"plant"}""")

private fun basilListItem() =
    fromDomainModel(Device(DeviceDomainModel("Basil", "a", DeviceType.PLANT), online = false))

/**
 * The MQTT connect source as the data layer reports it: [outcome] decides each connect (return to connect, throw to
 * fail: `IotAccessDeniedException` when AWS IoT refused the identity, another error for a socket or Cognito failure).
 * [calls] counts the connect attempts the device list asks for.
 */
private class ScriptedConnectable(var outcome: suspend () -> String) : Connectable {
    var calls = 0
        private set

    override suspend fun connect(): String {
        calls += 1
        return outcome()
    }
}

private class BasilSubscribable : DeviceSubscribable {
    override fun subscribeDevices(): Flow<DeviceEvent> = flowOf(basilInfoEvent())
}

private val noAccess: suspend () -> String = { throw IotAccessDeniedException() }
private val socketError: suspend () -> String = {
    throw IllegalStateException("MQTT connection not established: FAILED")
}
private val connects: suspend () -> String = { "CONNECTED" }

@OptIn(ExperimentalCoroutinesApi::class)
private fun runDevicesTest(block: suspend TestScope.(ViewModelStore) -> Unit) {
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

private fun devicesViewModel(store: ViewModelStore, connectable: Connectable): DevicesViewModel {
    val factory = viewModelFactory {
        initializer {
            DevicesViewModel(
                connect = ConnectUseCase(connectable),
                observeDevices = ObserveDevicesUseCase(BasilSubscribable()),
                mapper = ::fromDomainModel,
            )
        }
    }
    return ViewModelProvider.create(store, factory)[DevicesViewModel::class]
}

/**
 * STORY-2 AC46, AC47 and AC43 (the device list's part): a signed-in identity without AWS IoT access sees the empty
 * device list ("No devices yet" with "Search again"), not the error screen, and the list does not retry on its own. A
 * socket or Cognito failure still shows the error screen. The data layer's part (a refused connection becomes
 * `IotAccessDeniedException`, a socket error does not) is tested in core:data with fake transports.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Story2NoIotAccessDevicesViewModelTest : FunSpec({

    // AC46
    // GIVEN a signed-in session whose connection AWS IoT refuses (no AWS IoT access)
    // WHEN the device list connects
    // THEN its state becomes Success with an empty list, not Error
    test("testStory2_noIotAccessShowsTheEmptyDeviceListNotAnError") {
        runDevicesTest { store ->
            val target = devicesViewModel(store, ScriptedConnectable(noAccess))

            target.devices.test {
                awaitItem() shouldBe Loading
                awaitItem() shouldBe Success(emptyList())
            }
        }
    }

    // AC46
    // GIVEN a socket error before any answer from AWS (no network, DNS failure)
    // WHEN the device list connects
    // THEN its state is Error
    test("testStory2_socketErrorStillShowsTheErrorState") {
        runDevicesTest { store ->
            val target = devicesViewModel(store, ScriptedConnectable(socketError))

            target.devices.test {
                awaitItem() shouldBe Loading
                awaitItem().shouldBeInstanceOf<Error>()
            }
        }
    }

    // AC47
    // GIVEN the empty list after a refusal
    // WHEN 5 minutes pass with the screen open and untouched
    // THEN no further connect attempt is made
    test("testStory2_noRetryLoopAfterARefusal") {
        runDevicesTest { store ->
            val connectable = ScriptedConnectable(noAccess)
            val target = devicesViewModel(store, connectable)

            target.devices.test {
                awaitItem() shouldBe Loading
                awaitItem() shouldBe Success(emptyList())
                advanceTimeBy(FIVE_MINUTES_MILLIS)
                runCurrent()

                connectable.calls shouldBe 1
                expectNoEvents()
            }
        }
    }

    // AC47
    // GIVEN the empty list after a refusal
    // WHEN the user taps "Search again" (retry)
    // THEN exactly one new connect attempt is made
    test("testStory2_searchAgainAfterARefusalMakesExactlyOneAttempt") {
        runDevicesTest { store ->
            val connectable = ScriptedConnectable(noAccess)
            val target = devicesViewModel(store, connectable)

            target.devices.test {
                awaitItem() shouldBe Loading
                awaitItem() shouldBe Success(emptyList())

                target.retry()
                awaitItem() shouldBe Loading
                awaitItem() shouldBe Success(emptyList())
                advanceTimeBy(FIVE_MINUTES_MILLIS)
                runCurrent()

                connectable.calls shouldBe 2
            }
        }
    }

    // AC47
    // GIVEN the empty list after a refusal
    // WHEN the app returns to the foreground (refresh)
    // THEN exactly one new connect attempt is made
    test("testStory2_returnToForegroundAfterARefusalMakesExactlyOneAttempt") {
        runDevicesTest { store ->
            val connectable = ScriptedConnectable(noAccess)
            val target = devicesViewModel(store, connectable)

            target.devices.test {
                awaitItem() shouldBe Loading
                awaitItem() shouldBe Success(emptyList())

                target.refresh()
                advanceTimeBy(FIVE_MINUTES_MILLIS)
                runCurrent()

                connectable.calls shouldBe 2
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // AC47 / AC48 (the app's part of "after the admin approves, Search again shows the devices")
    // GIVEN the empty list after a refusal, and the identity now has access
    // WHEN the user taps "Search again"
    // THEN the list connects and shows the devices
    test("testStory2_searchAgainAfterAccessIsGrantedShowsTheDevices") {
        runDevicesTest { store ->
            val connectable = ScriptedConnectable(noAccess)
            val target = devicesViewModel(store, connectable)

            target.devices.test {
                awaitItem() shouldBe Loading
                awaitItem() shouldBe Success(emptyList())

                connectable.outcome = connects
                target.retry()

                awaitItem() shouldBe Loading
                awaitItem() shouldBe Success(listOf(basilListItem()))
            }
        }
    }

    // AC43 (the device list's part)
    // GIVEN a connect that fails because Cognito gave no credentials (the data layer reports FAILED)
    // WHEN the device list connects, then Cognito answers again and the user retries
    // THEN the list shows Error, then connects and shows the devices
    test("testStory2_credentialsFailureShowsErrorThenRetryConnects") {
        runDevicesTest { store ->
            val connectable = ScriptedConnectable(socketError)
            val target = devicesViewModel(store, connectable)

            target.devices.test {
                awaitItem() shouldBe Loading
                awaitItem().shouldBeInstanceOf<Error>()

                connectable.outcome = connects
                target.retry()

                awaitItem() shouldBe Loading
                awaitItem() shouldBe Success(listOf(basilListItem()))
            }
        }
    }
})
