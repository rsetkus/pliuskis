package lt.setkus.pliuskis.data.mqttclient

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private val settings = MqttSettings(
    clientId = "story2client",
    host = "localhost",
    port = 1883,
    tlsMode = TlsMode.NONE,
)

/** "Bazilikas Šilas" as UTF-8 bytes, spelled out so the test does not depend on the decoder it checks. Š is C5 A0. */
private val bazilikasSilasUtf8 =
    "Bazilikas ".encodeToByteArray() + byteArrayOf(0xC5.toByte(), 0xA0.toByte()) + "ilas".encodeToByteArray()

/** Connects [manager]: the broker accepts the attempt the manager starts on [transport]. */
@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun TestScope.connect(manager: IosMqttClientManager, transport: FakeMqttTransport) {
    val result = async { manager.connect() }
    runCurrent()
    transport.acceptConnection()
    result.await() shouldBe MqttConnectionState.CONNECTED
}

/** Collects `manager.subscribe(topicFilter)` into [into] until the returned job is cancelled. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.collect(
    manager: IosMqttClientManager,
    topicFilter: String,
    into: MutableList<IotMessage>,
): Job {
    val job = launch { manager.subscribe(topicFilter).collect { into += it } }
    runCurrent()
    return job
}

/**
 * STORY-2: the iOS [lt.setkus.pliuskis.data.IotManager] meets the same contract as Android's HiveMQ client. CocoaMQTT
 * (Swift) is only the socket behind [MqttTransport]; this Kotlin client owns connect de-duplication and the 10 s wait,
 * re-subscribing after every CONNACK, unsubscribing when a collector goes away, topic filter matching and UTF-8
 * decoding. [FakeMqttTransport] records what the client sends to the broker and plays the broker's side.
 *
 * The same behaviour against a real broker, through the Swift transport, is checked by
 * `iosApp/iosAppTests/Story2MqttBrokerContractTests.swift`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Story2IosMqttClientManagerTest : FunSpec({

    // AC11
    // GIVEN the client is CONNECTED
    // WHEN connect() is called again
    // THEN it returns CONNECTED and opens no second connection
    test("testStory2_connectWhileConnectedReturnsConnectedWithoutASecondConnection") {
        runTest {
            val transport = FakeMqttTransport()
            val manager = IosMqttClientManager(transport, settings)
            connect(manager, transport)

            val again = manager.connect()

            again shouldBe MqttConnectionState.CONNECTED
            transport.connectCalls.size shouldBe 1
        }
    }

    // AC11
    // GIVEN a connect attempt is in progress
    // WHEN a second connect() is called and then the broker accepts the first attempt
    // THEN no second attempt starts and both callers get CONNECTED
    test("testStory2_concurrentConnectJoinsTheRunningAttemptAndGetsItsResult") {
        runTest {
            val transport = FakeMqttTransport()
            val manager = IosMqttClientManager(transport, settings)

            val first = async { manager.connect() }
            val second = async { manager.connect() }
            runCurrent()
            transport.connectCalls.size shouldBe 1

            transport.acceptConnection()

            first.await() shouldBe MqttConnectionState.CONNECTED
            second.await() shouldBe MqttConnectionState.CONNECTED
            transport.connectCalls.size shouldBe 1
        }
    }

    // AC11
    // GIVEN a connect attempt is in progress
    // WHEN a second connect() is called and the first attempt fails (the broker refuses or the socket closes)
    // THEN the second caller gets the first attempt's result, FAILED, and no second attempt starts
    test("testStory2_concurrentConnectGetsFailedWhenTheRunningAttemptFails") {
        runTest {
            val transport = FakeMqttTransport()
            val manager = IosMqttClientManager(transport, settings)

            val first = async { manager.connect() }
            val second = async { manager.connect() }
            runCurrent()

            transport.dropConnection()

            first.await() shouldBe MqttConnectionState.FAILED
            second.await() shouldBe MqttConnectionState.FAILED
            transport.connectCalls.size shouldBe 1
        }
    }

    // AC11
    // GIVEN a connect attempt is in progress and the broker never answers
    // WHEN a second connect() is called
    // THEN it starts no new attempt, is still waiting just before 10 s, and returns FAILED at 10 s
    test("testStory2_concurrentConnectReturnsFailedWhenNotConnectedWithinTenSeconds") {
        runTest {
            val transport = FakeMqttTransport()
            val manager = IosMqttClientManager(transport, settings)

            async { manager.connect() }
            val second = async { manager.connect() }
            runCurrent()

            advanceTimeBy(9_999)
            runCurrent()
            second.isCompleted shouldBe false

            advanceTimeBy(2)
            runCurrent()
            second.isCompleted shouldBe true
            second.await() shouldBe MqttConnectionState.FAILED
            transport.connectCalls.size shouldBe 1
        }
    }

    // AC11
    // GIVEN the connection was lost and the transport is reconnecting on its own
    // WHEN connect() is called
    // THEN it starts no new attempt and returns CONNECTED when the automatic reconnect gets its CONNACK
    test("testStory2_connectDuringAutomaticReconnectWaitsForItInsteadOfStartingAnAttempt") {
        runTest {
            val transport = FakeMqttTransport()
            val manager = IosMqttClientManager(transport, settings)
            connect(manager, transport)
            transport.dropConnection()
            transport.startAutomaticReconnect()

            val result = async { manager.connect() }
            runCurrent()
            transport.connectCalls.size shouldBe 1

            transport.acceptConnection()

            result.await() shouldBe MqttConnectionState.CONNECTED
            transport.connectCalls.size shouldBe 1
        }
    }

    // AC9 (Kotlin side; the broker side is Story2MqttBrokerContractTests in iosApp)
    // GIVEN the client is connected
    // WHEN publishString("{"command":"water"}", "t/x") is called
    // THEN the transport publishes exactly those UTF-8 bytes on t/x
    test("testStory2_publishStringHandsTheExactPayloadAndTopicToTheTransport") {
        runTest {
            val transport = FakeMqttTransport()
            val manager = IosMqttClientManager(transport, settings)
            connect(manager, transport)

            val sent = async { manager.publishString("""{"command":"water"}""", "t/x") }
            runCurrent()
            transport.acknowledgePublish(transport.publishes.single().messageId)
            sent.await()

            val published = transport.publishes.single()
            published.topic shouldBe "t/x"
            published.payload shouldBe """{"command":"water"}""".encodeToByteArray()
        }
    }

    // AC10 (Kotlin side; the retained messages from a real broker are Story2MqttBrokerContractTests in iosApp)
    // GIVEN open flows on devices/+/info and devices/+/availability over the one transport
    // WHEN the broker delivers devices/a/info, devices/b/info and devices/a/availability
    // THEN the info flow emits both info messages with their own topics, and only those
    test("testStory2_wildcardSubscriptionEmitsEveryMatchingMessageWithItsTopic") {
        runTest {
            val transport = FakeMqttTransport()
            val manager = IosMqttClientManager(transport, settings)
            connect(manager, transport)
            val info = mutableListOf<IotMessage>()
            val availability = mutableListOf<IotMessage>()
            val infoJob = collect(manager, "devices/+/info", info)
            val availabilityJob = collect(manager, "devices/+/availability", availability)

            transport.deliver("devices/a/info", """{"name":"A"}""".encodeToByteArray())
            transport.deliver("devices/b/info", """{"name":"B"}""".encodeToByteArray())
            transport.deliver("devices/a/availability", "online".encodeToByteArray())
            runCurrent()

            info shouldBe listOf(
                IotMessage("devices/a/info", """{"name":"A"}"""),
                IotMessage("devices/b/info", """{"name":"B"}"""),
            )
            availability shouldBe listOf(IotMessage("devices/a/availability", "online"))
            infoJob.cancel()
            availabilityJob.cancel()
        }
    }

    // AC12
    // GIVEN an open subscribe("devices/+/availability") flow
    // WHEN the transport drops and reconnects with a new CONNACK
    // THEN SUBSCRIBE devices/+/availability is sent again, and a message after the reconnect reaches the same flow
    test("testStory2_openSubscriptionIsSubscribedAgainAfterReconnectAndKeepsEmitting") {
        runTest {
            val transport = FakeMqttTransport()
            val manager = IosMqttClientManager(transport, settings)
            connect(manager, transport)
            val received = mutableListOf<IotMessage>()
            val job = collect(manager, "devices/+/availability", received)
            transport.subscribes shouldBe listOf("devices/+/availability")

            transport.dropConnection()
            transport.startAutomaticReconnect()
            transport.acceptConnection()
            runCurrent()

            transport.subscribes shouldBe listOf("devices/+/availability", "devices/+/availability")

            transport.deliver("devices/basil/availability", "offline".encodeToByteArray())
            runCurrent()

            received shouldBe listOf(IotMessage("devices/basil/availability", "offline"))
            job.isActive shouldBe true
            job.cancel()
        }
    }

    // AC13
    // GIVEN a collected subscribe("t/x") flow
    // WHEN its collector is cancelled
    // THEN the transport receives exactly one UNSUBSCRIBE, for t/x
    test("testStory2_cancellingTheCollectorSendsExactlyOneUnsubscribe") {
        runTest {
            val transport = FakeMqttTransport()
            val manager = IosMqttClientManager(transport, settings)
            connect(manager, transport)
            val job = collect(manager, "t/x", mutableListOf())

            job.cancel()
            runCurrent()

            transport.unsubscribes shouldBe listOf("t/x")
        }
    }

    // AC14
    // GIVEN a collected subscribe("t/x") flow
    // WHEN a message arrives whose payload bytes are the UTF-8 encoding of "Bazilikas Šilas"
    // THEN the emitted IotMessage payload equals "Bazilikas Šilas"
    test("testStory2_payloadBytesAreDecodedAsUtf8") {
        runTest {
            val transport = FakeMqttTransport()
            val manager = IosMqttClientManager(transport, settings)
            connect(manager, transport)
            val received = mutableListOf<IotMessage>()
            val job = collect(manager, "t/x", received)

            transport.deliver("t/x", bazilikasSilasUtf8)
            runCurrent()

            received shouldBe listOf(IotMessage("t/x", "Bazilikas Šilas"))
            job.cancel()
        }
    }
})
