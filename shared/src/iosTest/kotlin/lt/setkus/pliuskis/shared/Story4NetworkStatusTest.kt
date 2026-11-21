package lt.setkus.pliuskis.shared

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.viewModelFactory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import lt.setkus.pliuskis.data.mqttclient.MqttSettings
import lt.setkus.pliuskis.data.mqttclient.MqttTransport
import lt.setkus.pliuskis.data.mqttclient.MqttTransportListener
import lt.setkus.pliuskis.data.mqttclient.TlsMode
import lt.setkus.pliuskis.data.util.NetworkConnectivity
import org.koin.core.context.loadKoinModules
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import platform.Foundation.NSData

private val settings = MqttSettings(
    clientId = "story4client",
    host = "story4-broker",
    port = 1883,
    tlsMode = TlsMode.NONE,
)

/** Stands in for the NWPathMonitor-backed connectivity: the test reports the device online or offline. */
private class Story4FakeConnectivity : NetworkConnectivity {
    private val updates = MutableSharedFlow<Boolean>(replay = 1)

    override val isOnline: Flow<Boolean> = updates

    fun report(online: Boolean) {
        check(updates.tryEmit(online)) { "The connectivity update was not delivered" }
    }
}

/** A socket that never answers; the network status screen state does not need the broker. */
private class Story4SilentTransport : MqttTransport {
    override var listener: MqttTransportListener? = null

    override fun connect(host: String, port: Int, clientId: String) = Unit

    override fun subscribe(topicFilter: String) = Unit

    override fun unsubscribe(topicFilter: String) = Unit

    override fun publish(topic: String, payload: NSData): Int = -1
}

/** Keeps the [NetworkStatusViewModel] a test creates, so it can be cleared before Main is reset. */
private class Story4Harness {
    private val store = ViewModelStore()

    fun keep(create: () -> NetworkStatusViewModel): NetworkStatusViewModel =
        ViewModelProvider.create(
            store,
            viewModelFactory { addInitializer(NetworkStatusViewModel::class) { create() } },
        )[NetworkStatusViewModel::class]

    fun clear() = store.clear()
}

/** Runs [block] with a test Main dispatcher; the ViewModel runs in its viewModelScope. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun runWithMain(block: suspend TestScope.(Story4Harness) -> Unit) {
    val dispatcher = StandardTestDispatcher()
    Dispatchers.setMain(dispatcher)
    try {
        runTest(dispatcher) {
            val harness = Story4Harness()
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
 * STORY-4 AC26 (Kotlin side): the shared connectivity state Swift shows the offline banner from. On Android the banner
 * is app-level (`PliuskisAppState.isOffline`), not part of `DevicesViewModel`; iOS gets the same meaning from
 * `NetworkStatusViewModel.isOffline`, created with `createNetworkStatusViewModel()` and owned by the screen through
 * `IosViewModelStoreOwner`. The Swift half (banner shown, then gone) is `Story4DeviceListUITests` in `iosApp`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Story4NetworkStatusTest : FunSpec({

    // AC26
    // GIVEN the screen collects isOffline
    // WHEN the connectivity reports offline, then online
    // THEN isOffline is true (banner shown), then false (banner gone)
    test("testStory4_isOfflineIsTrueWhenConnectivityIsOfflineAndFalseWhenItComesBackOnline") {
        runWithMain { harness ->
            val connectivity = Story4FakeConnectivity()
            val target = harness.keep { NetworkStatusViewModel(connectivity) }
            backgroundScope.launch { target.isOffline.collect {} }
            runCurrent()

            connectivity.report(online = false)
            runCurrent()
            target.isOffline.value shouldBe true

            connectivity.report(online = true)
            runCurrent()
            target.isOffline.value shouldBe false
        }
    }

    // AC26
    // GIVEN initShared has started the graph and the graph's NetworkConnectivity reports offline
    // WHEN Swift creates the state with createNetworkStatusViewModel() and collects isOffline
    // THEN it follows the graph's connectivity: true, then false once it is online again
    test("testStory4_networkStatusFromFactoryFollowsTheConnectivityOfTheSharedGraph") {
        val connectivity = Story4FakeConnectivity()
        initShared(settings, Story4SilentTransport())
        try {
            // Replaces the NWPathMonitor-backed connectivity of the iOS graph with the fake (Koin allows overrides).
            loadKoinModules(module { single<NetworkConnectivity> { connectivity } })
            runWithMain { harness ->
                val target = harness.keep { createNetworkStatusViewModel() }
                backgroundScope.launch { target.isOffline.collect {} }
                runCurrent()

                connectivity.report(online = false)
                runCurrent()
                target.isOffline.value shouldBe true

                connectivity.report(online = true)
                runCurrent()
                target.isOffline.value shouldBe false
            }
        } finally {
            stopKoin()
        }
    }
})
