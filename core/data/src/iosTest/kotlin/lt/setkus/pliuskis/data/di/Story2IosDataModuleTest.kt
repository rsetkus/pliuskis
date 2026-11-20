package lt.setkus.pliuskis.data.di

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import lt.setkus.pliuskis.data.IotManager
import lt.setkus.pliuskis.data.mqttclient.FakeMqttTransport
import lt.setkus.pliuskis.data.mqttclient.MqttConnectionState
import lt.setkus.pliuskis.data.mqttclient.MqttSettings
import lt.setkus.pliuskis.data.mqttclient.TlsMode
import lt.setkus.pliuskis.data.util.FakeNetworkPathSource
import lt.setkus.pliuskis.data.util.NetworkConnectivity
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module

private const val NW_PATH_MONITOR_FIRST_UPDATE_TIMEOUT_MILLIS = 5_000L

private val settings = MqttSettings(
    clientId = "story2graph",
    host = "localhost",
    port = 1883,
    tlsMode = TlsMode.NONE,
)

/** Runs [block] on an isolated Koin graph of the settings `initShared` binds plus [iosModule]. */
private suspend fun withIosGraph(iosModule: Module, block: suspend (Koin) -> Unit) {
    val app = koinApplication { modules(module { single { settings } }, iosModule) }
    try {
        block(app.koin)
    } finally {
        app.close()
    }
}

/**
 * STORY-2 AC16: `iosDataModule` binds [IotManager] to the transport-backed iOS MQTT client and [NetworkConnectivity]
 * to the path-source-backed iOS monitor (NWPathMonitor by default). Checked by what the resolved objects do, not by
 * their class names. "NotImplementedIotManager is removed" and the Shared.h header check are proven by command and
 * review in the story's implementation map.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Story2IosDataModuleTest : FunSpec({

    // AC16
    // GIVEN the iOS graph built with a transport
    // WHEN the resolved IotManager connects
    // THEN it connects through that transport with the bound settings and reports CONNECTED on the CONNACK
    test("testStory2_iosGraphResolvesIotManagerToTheTransportBackedClient") {
        val transport = FakeMqttTransport()
        withIosGraph(iosDataModule(transport, FakeNetworkPathSource())) { koin ->
            val manager = koin.get<IotManager>()

            runTest {
                val result = async { manager.connect() }
                runCurrent()
                transport.connectCalls shouldBe listOf(FakeMqttTransport.ConnectCall("localhost", 1883, "story2graph"))

                transport.acceptConnection()

                result.await() shouldBe MqttConnectionState.CONNECTED
            }
            manager.getClientId() shouldBe "story2graph"
        }
    }

    // AC16
    // GIVEN the iOS graph built with a path source
    // WHEN the resolved NetworkConnectivity is collected and the path goes unsatisfied, then satisfied
    // THEN isOnline follows that path source: false, then true
    test("testStory2_iosGraphResolvesNetworkConnectivityToThePathSourceBackedMonitor") {
        val pathSource = FakeNetworkPathSource()
        withIosGraph(iosDataModule(FakeMqttTransport(), pathSource)) { koin ->
            val connectivity = koin.get<NetworkConnectivity>()

            runTest {
                val received = mutableListOf<Boolean>()
                val job = launch { connectivity.isOnline.toList(received) }
                runCurrent()

                pathSource.report(satisfied = false)
                runCurrent()
                pathSource.report(satisfied = true)
                runCurrent()

                received shouldBe listOf(false, true)
                job.cancel()
            }
        }
    }

    // AC16
    // GIVEN the iOS graph built with only a transport (the default path source, NWPathMonitor)
    // WHEN the resolved NetworkConnectivity is collected on the Simulator
    // THEN NWPathMonitor's first path update arrives as an isOnline value within 5 s
    test("testStory2_defaultIosGraphReportsTheNetworkPathFromNwPathMonitor") {
        withIosGraph(iosDataModule(FakeMqttTransport())) { koin ->
            val connectivity = koin.get<NetworkConnectivity>()

            // The value depends on the Mac's network; the check is that a real path update arrives at all.
            // withTimeout throws, and so fails the test, if NWPathMonitor never reports a path.
            withContext(Dispatchers.Default) {
                withTimeout(NW_PATH_MONITOR_FIRST_UPDATE_TIMEOUT_MILLIS) { connectivity.isOnline.first() }
            }
        }
    }
})
