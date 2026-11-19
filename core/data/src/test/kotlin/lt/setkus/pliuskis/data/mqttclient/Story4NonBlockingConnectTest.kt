package lt.setkus.pliuskis.data.mqttclient

import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientConfig
import com.hivemq.client.mqtt.mqtt3.Mqtt3RxClient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

private const val PENDING_TIMEOUT_MILLIS = 10_000L
private const val JUST_BEFORE_TIMEOUT_MILLIS = 9_900L
private const val PAST_TIMEOUT_MILLIS = 200L
private const val ATTEMPT_FINISHES_AFTER_MILLIS = 3_000L
private const val STATE_PICKUP_MILLIS = 1_000L

/**
 * HiveMQ client whose [currentState] the test changes, as another connect attempt (a first caller or HiveMQ's automatic
 * reconnect) would. The manager must only watch that attempt: asking for any client view (blocking, async or Rx)
 * could start a second connect, so it is recorded in [connectViewRequested] and fails the call.
 */
private class PendingAttemptMqtt3Client(var currentState: MqttClientState) : Mqtt3Client {
    var connectViewRequested = false
        private set

    override fun getState(): MqttClientState = currentState

    override fun toBlocking(): Mqtt3BlockingClient = refuse()

    override fun toAsync(): Mqtt3AsyncClient = refuse()

    override fun toRx(): Mqtt3RxClient = refuse()

    override fun getConfig(): Mqtt3ClientConfig = error("config is not used by the connect path")

    private fun refuse(): Nothing {
        connectViewRequested = true
        error("a connect attempt is already running; the manager must not start another one")
    }
}

/**
 * STORY-4: Make IotManager non-blocking.
 * Covers AC14 (safe to call when already connected) and AC16 (a second caller waits for the running attempt, with
 * a 10 s limit) on [HiveMqttClientManager] with its default timeout, in virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Story4NonBlockingConnectTest : FunSpec({

    // AC14
    // GIVEN a client that is already connected
    // WHEN connect() is called
    // THEN should report CONNECTED at once, without starting a connect
    test("testStory4_connectOnConnectedClientReturnsConnectedWithoutConnecting") {
        runTest {
            val client = PendingAttemptMqtt3Client(MqttClientState.CONNECTED)

            HiveMqttClientManager(client).connect() shouldBe MqttConnectionState.CONNECTED

            client.connectViewRequested.shouldBeFalse()
            currentTime shouldBe 0L
        }
    }

    // AC16
    // GIVEN a connect attempt in progress (client CONNECTING) that succeeds after 3 s
    // WHEN a second caller calls connect()
    // THEN it should suspend until that attempt finishes and then report its result, CONNECTED,
    // without a second connect
    test("testStory4_secondCallerWaitsForRunningAttemptAndGetsItsResult") {
        runTest {
            val client = PendingAttemptMqtt3Client(MqttClientState.CONNECTING)
            val manager = HiveMqttClientManager(client)

            val result = async { manager.connect() }
            testScheduler.advanceTimeBy(ATTEMPT_FINISHES_AFTER_MILLIS)
            testScheduler.runCurrent()
            result.isCompleted.shouldBeFalse()

            client.currentState = MqttClientState.CONNECTED
            testScheduler.advanceTimeBy(STATE_PICKUP_MILLIS)
            testScheduler.runCurrent()

            result.isCompleted.shouldBeTrue()
            result.await() shouldBe MqttConnectionState.CONNECTED
            currentTime shouldBeLessThan PENDING_TIMEOUT_MILLIS
            client.connectViewRequested.shouldBeFalse()
        }
    }

    // AC16
    // GIVEN a connect attempt in progress that two callers join
    // WHEN that attempt succeeds
    // THEN both callers should get CONNECTED, and neither starts a connect
    test("testStory4_everyWaitingCallerGetsTheResultOfTheRunningAttempt") {
        runTest {
            val client = PendingAttemptMqtt3Client(MqttClientState.CONNECTING_RECONNECT)
            val manager = HiveMqttClientManager(client)

            val first = async { manager.connect() }
            val second = async { manager.connect() }
            testScheduler.advanceTimeBy(ATTEMPT_FINISHES_AFTER_MILLIS)
            testScheduler.runCurrent()
            first.isCompleted.shouldBeFalse()
            second.isCompleted.shouldBeFalse()

            client.currentState = MqttClientState.CONNECTED
            testScheduler.advanceTimeBy(STATE_PICKUP_MILLIS)
            testScheduler.runCurrent()

            first.await() shouldBe MqttConnectionState.CONNECTED
            second.await() shouldBe MqttConnectionState.CONNECTED
            client.connectViewRequested.shouldBeFalse()
        }
    }

    // AC16
    // GIVEN a connect attempt in progress that never finishes (client stays CONNECTING)
    // WHEN a second caller calls connect()
    // THEN it should still be waiting just before 10 s and report FAILED once 10 s have passed,
    // without a second connect
    test("testStory4_secondCallerFailsWhenRunningAttemptTakesLongerThanTenSeconds") {
        runTest {
            val client = PendingAttemptMqtt3Client(MqttClientState.CONNECTING)
            val manager = HiveMqttClientManager(client)

            val result = async { manager.connect() }
            testScheduler.advanceTimeBy(JUST_BEFORE_TIMEOUT_MILLIS)
            testScheduler.runCurrent()
            result.isCompleted.shouldBeFalse()

            testScheduler.advanceTimeBy(PAST_TIMEOUT_MILLIS)
            testScheduler.runCurrent()

            result.isCompleted.shouldBeTrue()
            result.await() shouldBe MqttConnectionState.FAILED
            client.connectViewRequested.shouldBeFalse()
        }
    }

    // AC16
    // GIVEN HiveMQ's automatic reconnect is waiting to reconnect (DISCONNECTED_RECONNECT) and never connects
    // WHEN connect() is called
    // THEN should report FAILED after the 10 s limit, without starting its own connect
    test("testStory4_reconnectThatNeverConnectsFailsAfterTenSeconds") {
        runTest {
            val client = PendingAttemptMqtt3Client(MqttClientState.DISCONNECTED_RECONNECT)
            val manager = HiveMqttClientManager(client)

            val result = async { manager.connect() }
            testScheduler.advanceTimeBy(JUST_BEFORE_TIMEOUT_MILLIS)
            testScheduler.runCurrent()
            result.isCompleted.shouldBeFalse()

            testScheduler.advanceTimeBy(PAST_TIMEOUT_MILLIS)
            testScheduler.runCurrent()

            result.await() shouldBe MqttConnectionState.FAILED
            client.connectViewRequested.shouldBeFalse()
        }
    }
})
