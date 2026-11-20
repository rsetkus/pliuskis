package lt.setkus.pliuskis.data.mqttclient

import app.cash.turbine.test
import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedContext
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import lt.setkus.pliuskis.core.connect.Reconnectable
import lt.setkus.pliuskis.data.IotManager
import lt.setkus.pliuskis.data.di.androidDataModule
import lt.setkus.pliuskis.data.di.dataModule
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module

private val settings = MqttSettings(
    clientId = "story6android",
    host = "10.0.2.2",
    port = 1883,
    tlsMode = TlsMode.NONE,
)

/**
 * Runs [block] with the Android data graph the app builds ([dataModule], [androidDataModule], the app's
 * [MqttSettings]). Building the HiveMQ client does not connect, so no broker is needed.
 */
private suspend fun withAndroidGraph(block: suspend (Koin) -> Unit) {
    val app = koinApplication { modules(dataModule, androidDataModule, module { single { settings } }) }
    try {
        block(app.koin)
    } finally {
        app.close()
    }
}

/**
 * What HiveMQ does when the broker accepts a connection (the first one or an automatic reconnect after a lost
 * connection): it calls every connected listener registered in the client's configuration.
 */
private fun Mqtt3Client.receiveConnack() {
    val context = mockk<MqttClientConnectedContext>(relaxed = true)
    config.connectedListeners.forEach { it.onConnected(context) }
}

/**
 * STORY-6 AC44 on Android: the shared ControlViewModel asks for status again after the MQTT client reconnects on its
 * own, so the HiveMQ client must report each reconnect. `IotManager.reconnects()` emits once for every CONNACK after a
 * lost connection, and not for the client's first connection; the domain port [Reconnectable] from the data graph
 * reports the same events. The tests play HiveMQ's CONNACKs on the client the Koin graph builds.
 */
class Story6HiveMqttReconnectsTest : FunSpec({

    // AC44
    // GIVEN the Android graph's HiveMQ client and a collector of IotManager.reconnects()
    // WHEN the broker accepts the client's first connection
    // THEN reconnects() emits nothing
    test("testStory6_firstConnackIsNotAReconnect") {
        withAndroidGraph { koin ->
            val client = koin.get<Mqtt3Client>()
            val target = koin.get<IotManager>()

            target.reconnects().test {
                client.receiveConnack()

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // AC44
    // GIVEN the client connected once
    // WHEN the connection is lost and HiveMQ reconnects twice (two more CONNACKs)
    // THEN reconnects() emits exactly once per reconnect CONNACK
    test("testStory6_everyConnackAfterTheFirstEmitsOneReconnect") {
        withAndroidGraph { koin ->
            val client = koin.get<Mqtt3Client>()
            val target = koin.get<IotManager>()

            target.reconnects().test {
                client.receiveConnack()
                expectNoEvents()

                client.receiveConnack()
                awaitItem() shouldBe Unit
                expectNoEvents()

                client.receiveConnack()
                awaitItem() shouldBe Unit
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // AC44: the control screen opens after the first connection (the device list connected the client)
    // GIVEN the client connected before anyone collected reconnects()
    // WHEN a collector starts and then HiveMQ reconnects after a lost connection
    // THEN that collector gets exactly one reconnect
    test("testStory6_collectorStartedAfterFirstConnectGetsTheNextReconnect") {
        withAndroidGraph { koin ->
            val client = koin.get<Mqtt3Client>()
            val target = koin.get<IotManager>()
            client.receiveConnack()

            target.reconnects().test {
                expectNoEvents()

                client.receiveConnack()

                awaitItem() shouldBe Unit
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // AC44: the ViewModel side reads reconnects through the domain port, not IotManager
    // GIVEN the Android data graph, connected once
    // WHEN HiveMQ reconnects after a lost connection
    // THEN the Reconnectable the graph provides emits exactly one reconnect
    test("testStory6_reconnectableFromDataGraphReportsHiveMqReconnects") {
        withAndroidGraph { koin ->
            val client = koin.get<Mqtt3Client>()
            val target = koin.get<Reconnectable>()

            target.reconnects().test {
                client.receiveConnack()
                expectNoEvents()

                client.receiveConnack()

                awaitItem() shouldBe Unit
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
})
