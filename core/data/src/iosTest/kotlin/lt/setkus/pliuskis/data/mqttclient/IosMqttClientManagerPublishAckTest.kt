package lt.setkus.pliuskis.data.mqttclient

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private val settings = MqttSettings(
    clientId = "pubackclient",
    host = "localhost",
    port = 1883,
    tlsMode = TlsMode.NONE,
)

private const val WATER = """{"command":"water"}"""
private const val WATER_TOPIC = "devices/pico-1/command"

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun TestScope.connectedManager(transport: FakeMqttTransport): IosMqttClientManager {
    val manager = IosMqttClientManager(transport, settings)
    val result = async { manager.connect() }
    runCurrent()
    transport.acceptConnection()
    result.await() shouldBe MqttConnectionState.CONNECTED
    return manager
}

/**
 * The iOS client's publishString returns only when the broker took the message (PUBACK), like HiveMQ's QoS 1 publish
 * on Android, and throws otherwise. CocoaMQTT queues a QoS 1 message even while disconnected, so the packet identifier
 * it returns proves nothing; without this the water button showed "Watering started" for a command that never left
 * the phone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IosMqttClientManagerPublishAckTest : FunSpec({

    // GIVEN the client is connected
    // WHEN publishString is called and the broker has not answered yet
    // THEN it keeps waiting, and returns once the broker sends PUBACK for that message
    test("publishStringReturnsOnlyAfterThePubackOfItsMessage") {
        runTest {
            val transport = FakeMqttTransport()
            val manager = connectedManager(transport)

            val sent = async { manager.publishString(WATER, WATER_TOPIC) }
            runCurrent()
            sent.isCompleted shouldBe false

            transport.acknowledgePublish(transport.publishes.single().messageId)
            runCurrent()

            sent.isCompleted shouldBe true
            sent.await()
        }
    }

    // GIVEN the client never connected
    // WHEN publishString is called
    // THEN it throws at once and hands nothing to the transport
    test("publishStringThrowsWithoutSendingWhenNotConnected") {
        runTest {
            val transport = FakeMqttTransport()
            val manager = IosMqttClientManager(transport, settings)

            shouldThrow<MqttPublishException> { manager.publishString(WATER, WATER_TOPIC) }

            transport.publishes.shouldBeEmpty()
        }
    }

    // GIVEN the client was connected and the connection dropped
    // WHEN publishString is called
    // THEN it throws at once and hands nothing to the transport
    test("publishStringThrowsWithoutSendingAfterTheConnectionDropped") {
        runTest {
            val transport = FakeMqttTransport()
            val manager = connectedManager(transport)
            transport.dropConnection()

            shouldThrow<MqttPublishException> { manager.publishString(WATER, WATER_TOPIC) }

            transport.publishes.shouldBeEmpty()
        }
    }

    // GIVEN the client is connected
    // WHEN the transport refuses the message (CocoaMQTT returns -1)
    // THEN publishString throws
    test("publishStringThrowsWhenTheTransportRefusesTheMessage") {
        runTest {
            val transport = FakeMqttTransport().apply { refusePublishes = true }
            val manager = connectedManager(transport)

            shouldThrow<MqttPublishException> { manager.publishString(WATER, WATER_TOPIC) }
        }
    }

    // GIVEN a publish is waiting for its PUBACK
    // WHEN the connection drops
    // THEN publishString throws
    test("publishStringThrowsWhenTheConnectionDropsBeforeThePuback") {
        runTest {
            val transport = FakeMqttTransport()
            val manager = connectedManager(transport)

            val sent = async { runCatching { manager.publishString(WATER, WATER_TOPIC) } }
            runCurrent()
            transport.dropConnection()

            (sent.await().exceptionOrNull() is MqttPublishException) shouldBe true
        }
    }

    // GIVEN a publish is waiting and the broker never answers
    // WHEN 10 s pass
    // THEN publishString is still waiting just before 10 s and throws at 10 s
    test("publishStringThrowsWhenNoPubackComesWithinTenSeconds") {
        runTest {
            val transport = FakeMqttTransport()
            val manager = connectedManager(transport)

            val sent = async { runCatching { manager.publishString(WATER, WATER_TOPIC) } }
            runCurrent()

            advanceTimeBy(PUBLISH_ACK_TIMEOUT_MILLIS - 1)
            runCurrent()
            sent.isCompleted shouldBe false

            advanceTimeBy(2)
            runCurrent()
            (sent.await().exceptionOrNull() is MqttPublishException) shouldBe true
        }
    }

    // GIVEN a publish timed out waiting for its PUBACK
    // WHEN the late PUBACK arrives, and a second publish gets the broker's answer
    // THEN the late PUBACK is ignored and the second publish returns
    test("aLatePubackOfATimedOutPublishIsIgnored") {
        runTest {
            val transport = FakeMqttTransport()
            val manager = connectedManager(transport)

            val first = async { runCatching { manager.publishString(WATER, WATER_TOPIC) } }
            runCurrent()
            advanceTimeBy(PUBLISH_ACK_TIMEOUT_MILLIS + 1)
            runCurrent()
            (first.await().exceptionOrNull() is MqttPublishException) shouldBe true

            transport.acknowledgePublish(transport.publishes[0].messageId)
            val second = async { manager.publishString(WATER, WATER_TOPIC) }
            runCurrent()
            transport.acknowledgePublish(transport.publishes[1].messageId)
            second.await()
        }
    }
})
