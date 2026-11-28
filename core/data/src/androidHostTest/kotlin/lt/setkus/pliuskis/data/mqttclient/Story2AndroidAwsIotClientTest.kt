package lt.setkus.pliuskis.data.mqttclient

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldNotBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import lt.setkus.pliuskis.core.auth.AuthTokens
import lt.setkus.pliuskis.core.auth.Authenticator
import lt.setkus.pliuskis.core.auth.TokenStore
import lt.setkus.pliuskis.core.connect.IotAccessDeniedException
import lt.setkus.pliuskis.data.ConnectMqttSource
import lt.setkus.pliuskis.data.IotManager
import lt.setkus.pliuskis.data.auth.SignInSettings
import lt.setkus.pliuskis.data.aws.AwsIotCredentialsCache
import lt.setkus.pliuskis.data.aws.AwsIotSettings
import lt.setkus.pliuskis.data.aws.FakeAuthenticator
import lt.setkus.pliuskis.data.aws.FakeClock
import lt.setkus.pliuskis.data.aws.FakeCognitoIdentityPool
import lt.setkus.pliuskis.data.aws.IDENTITY_A
import lt.setkus.pliuskis.data.aws.IDENTITY_B
import lt.setkus.pliuskis.data.aws.INSTALL_SUFFIX
import lt.setkus.pliuskis.data.aws.IdentityPoolAnswer
import lt.setkus.pliuskis.data.aws.InMemoryTokenStore
import lt.setkus.pliuskis.data.aws.ONE_MINUTE
import lt.setkus.pliuskis.data.aws.TEST_ACCESS_KEY_ID
import lt.setkus.pliuskis.data.aws.TEST_ENDPOINT
import lt.setkus.pliuskis.data.aws.TEST_ID_TOKEN
import lt.setkus.pliuskis.data.aws.TEST_SECRET_KEY
import lt.setkus.pliuskis.data.aws.TEST_USER_POOL_ID
import lt.setkus.pliuskis.data.aws.credentialAccessKeyId
import lt.setkus.pliuskis.data.aws.signingDate
import lt.setkus.pliuskis.data.aws.testAwsIotSettings
import lt.setkus.pliuskis.data.aws.testCredentialsCache
import lt.setkus.pliuskis.data.aws.tokensOf
import lt.setkus.pliuskis.data.di.androidDataModule
import lt.setkus.pliuskis.data.di.dataModule
import lt.setkus.pliuskis.data.systemstate.AwsSystemStateRequest
import lt.setkus.pliuskis.data.systemstate.SubscribeSystemStatusTopic
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

private const val AWS_IOT_PORT = 443
private const val CONTRACT_TIMEOUT_MILLIS = 10_000L
private const val WAIT_MILLIS = 10_000L
private const val FAST_RECONNECT_MILLIS = 100L
private const val SLOW_RECONNECT_MILLIS = 60_000L

/** Polls [condition] until it holds or [timeoutMillis] pass; fails with [what] then. */
private suspend fun awaitUntil(what: String, timeoutMillis: Long = WAIT_MILLIS, condition: () -> Boolean) {
    val start = System.currentTimeMillis()
    while (!condition()) {
        withClue("Timed out waiting until $what") {
            (System.currentTimeMillis() - start) shouldBeLessThanOrEqual timeoutMillis
        }
        delay(20)
    }
}

/**
 * The Android release client over fakes: the production [AwsIotMqttClientManager] (HiveMQ over WebSocket) with the
 * production credentials cache over a fake identity pool, a fake session and a fake clock (the signing time of every
 * URL), connecting to [broker] in plain `ws://` instead of `wss://<endpoint>:443`. [reconnectDelayMillis] is the wait
 * before an automatic reconnect.
 */
private class AndroidAwsIotHarness(
    val broker: FakeAwsIotBroker,
    tokens: AuthTokens? = tokensOf(),
    installSuffix: String = INSTALL_SUFFIX,
    accessKeyIds: List<String> = listOf(TEST_ACCESS_KEY_ID),
    reconnectDelayMillis: Long = FAST_RECONNECT_MILLIS,
) : AutoCloseable {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock = FakeClock()
    val pool = FakeCognitoIdentityPool(clock, nextAccessKeyIds = accessKeyIds)
    val tokenStore = InMemoryTokenStore(tokens)
    val credentials = testCredentialsCache(pool, tokenStore, scope)
    val manager = AwsIotMqttClientManager(
        settings = MqttSettings(installSuffix, "localhost", broker.port, TlsMode.WEBSOCKET_SIGV4),
        credentials = credentials,
        target = AwsIotWebSocketTarget(host = "localhost", port = broker.port, tls = false),
        scope = scope,
        reconnectDelayMillis = { reconnectDelayMillis },
    )

    override fun close() {
        scope.cancel()
    }
}

private fun <T> withBrokerAndClient(
    tokens: AuthTokens? = tokensOf(),
    accessKeyIds: List<String> = listOf(TEST_ACCESS_KEY_ID),
    reconnectDelayMillis: Long = FAST_RECONNECT_MILLIS,
    block: suspend AndroidAwsIotHarness.() -> T,
): T = FakeAwsIotBroker().start().use { broker ->
    AndroidAwsIotHarness(broker, tokens, accessKeyIds = accessKeyIds, reconnectDelayMillis = reconnectDelayMillis)
        .use { harness -> runBlocking { withTimeout(60.seconds) { harness.block() } } }
}

/** Records every Kermit message (and its throwable) while [block] runs, then restores the platform writer. */
private class RecordingLogWriter : LogWriter() {
    val lines: MutableList<String> = CopyOnWriteArrayList()

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        lines += "$tag $message ${throwable?.toString().orEmpty()} ${throwable?.cause?.toString().orEmpty()}"
    }
}

/** What `app` declares for a release build, over fakes: the prod-shaped settings, the stack and the session. */
private fun releaseAppModule(
    tokenStore: TokenStore,
    authenticator: Authenticator,
    tlsSetupAsked: AtomicBoolean,
): Module = module {
    single { MqttSettings(INSTALL_SUFFIX, TEST_ENDPOINT, AWS_IOT_PORT, TlsMode.WEBSOCKET_SIGV4) }
    single<AwsIotSettings> { testAwsIotSettings() }
    single { SignInSettings(TEST_USER_POOL_ID, "test-client", "test.auth.eu-north-1.amazoncognito.com", "test://in") }
    single<TokenStore> { tokenStore }
    single<Authenticator> { authenticator }
    single<MqttTlsSetup> {
        tlsSetupAsked.set(true)
        error("A release build over Cognito must not use the client certificate")
    }
}

private fun koinGraph(vararg modules: Module): KoinApplication =
    koinApplication { modules(listOf(dataModule, androidDataModule) + modules) }

/**
 * STORY-2 on Android: the release MQTT client (HiveMQ over a SigV4-signed WebSocket) against a fake AWS IoT in the
 * test JVM. AC35, AC40, AC41, AC43, AC44, AC45, AC46 and AC47 (the client's part), AC50 and AC53 (shared and Android
 * code), and AC51's "no Cognito call".
 */
class Story2AndroidAwsIotClientTest : FunSpec({

    // AC35
    // GIVEN the release settings for endpoint a1b2c3d4e5f6g7-ats.iot.eu-north-1.amazonaws.com and region eu-north-1
    // WHEN the Android data graph gives the client's connection target
    // THEN it is wss://<endpoint>:443/mqtt
    test("testStory2_releaseGraphTargetsTheEndpointOverWssOnPort443") {
        val graph = koinGraph(releaseAppModule(InMemoryTokenStore(), FakeAuthenticator(), AtomicBoolean(false)))
        try {
            graph.koin.get<MqttSettings>().connectionTarget shouldBe
                "wss://a1b2c3d4e5f6g7-ats.iot.eu-north-1.amazonaws.com:443/mqtt"
            graph.koin.get<AwsIotWebSocketTarget>() shouldBe
                AwsIotWebSocketTarget(host = TEST_ENDPOINT, port = AWS_IOT_PORT, tls = true)
        } finally {
            graph.close()
        }
    }

    // AC35
    // GIVEN the release graph, with an MqttTlsSetup that records being asked for, pointed at the fake AWS IoT
    // WHEN the IotManager is built and connects
    // THEN it connects over a SigV4-signed WebSocket (iotdevicegateway scope) and never asks for the MqttTlsSetup
    test("testStory2_releaseGraphConnectsWithSigV4AndNeverAsksForTheClientCertificate") {
        FakeAwsIotBroker().start().use { broker ->
            val tlsSetupAsked = AtomicBoolean(false)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val clock = FakeClock()
            val pool = FakeCognitoIdentityPool(clock)
            val tokenStore = InMemoryTokenStore()
            val graph = koinGraph(
                releaseAppModule(tokenStore, FakeAuthenticator(), tlsSetupAsked),
                module {
                    single<AwsIotCredentialsCache> { testCredentialsCache(pool, tokenStore, scope) }
                    single { AwsIotWebSocketTarget(host = "localhost", port = broker.port, tls = false) }
                },
            )
            try {
                val state = runBlocking { graph.koin.get<IotManager>().connect() }

                state shouldBe MqttConnectionState.CONNECTED
                val uri = broker.handshakeUris.single()
                uri shouldStartWith "/mqtt?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential="
                uri shouldContain "%2Feu-north-1%2Fiotdevicegateway%2Faws4_request"
                uri shouldContain "&X-Amz-Signature="
                tlsSetupAsked.get().shouldBeFalse()
            } finally {
                graph.close()
                scope.cancel()
            }
        }
    }

    // AC40 (Android)
    // GIVEN the clock at 12:00:00Z, a first connect, a dropped connection, and the clock at 12:10:00Z
    // WHEN connect() is called before the automatic reconnect is due
    // THEN the two attempts used two different URLs, signed at 12:00:00Z and 12:10:00Z
    test("testStory2_androidEveryConnectUsesANewlySignedUrl") {
        withBrokerAndClient(reconnectDelayMillis = SLOW_RECONNECT_MILLIS) {
            manager.connect() shouldBe MqttConnectionState.CONNECTED
            broker.dropConnections()
            delay(FAST_RECONNECT_MILLIS * 3)

            clock.epochSeconds += 10 * ONE_MINUTE
            manager.connect() shouldBe MqttConnectionState.CONNECTED

            broker.handshakeUris shouldHaveSize 2
            broker.handshakeUris.map { it.signingDate } shouldBe listOf("20261121T120000Z", "20261121T121000Z")
            broker.handshakeUris[0] shouldNotBe broker.handshakeUris[1]
        }
    }

    // AC41 (Android)
    // GIVEN a client connected at 12:00:00Z whose connection drops at 12:10:00Z
    // WHEN HiveMQ reconnects on its own, without any connect() call
    // THEN the reconnect's WebSocket URL is signed at 12:10:00Z, not the first connection's URL
    test("testStory2_androidHiveMqAutomaticReconnectUsesAUrlSignedAtThatAttempt") {
        withBrokerAndClient {
            manager.connect() shouldBe MqttConnectionState.CONNECTED
            clock.epochSeconds += 10 * ONE_MINUTE

            broker.dropConnections()
            awaitUntil("HiveMQ reconnected on its own") { broker.connects.size == 2 }

            broker.handshakeUris shouldHaveSize 2
            broker.handshakeUris[1].signingDate shouldBe "20261121T121000Z"
            broker.handshakeUris[1] shouldNotBe broker.handshakeUris[0]
        }
    }

    // AC43 (Android)
    // GIVEN a signed-in session and an identity pool that cannot be reached (network error)
    // WHEN connect() is called
    // THEN it returns FAILED within 10 s and no socket is opened
    test("testStory2_androidCognitoNetworkErrorFailsTheConnectWithoutOpeningASocket") {
        withBrokerAndClient {
            pool.answer = IdentityPoolAnswer.NETWORK_ERROR

            val start = System.currentTimeMillis()
            val state = manager.connect()

            state shouldBe MqttConnectionState.FAILED
            (System.currentTimeMillis() - start) shouldBeLessThanOrEqual CONTRACT_TIMEOUT_MILLIS
            broker.handshakeUris shouldHaveSize 0
        }
    }

    // AC43 (Android)
    // GIVEN a signed-in session whose ID token Cognito rejects (NotAuthorizedException), also after a token refresh
    // WHEN connect() is called
    // THEN it returns FAILED within 10 s and no socket is opened
    test("testStory2_androidRejectedIdTokenFailsTheConnectWithoutOpeningASocket") {
        withBrokerAndClient {
            pool.answer = IdentityPoolAnswer.NOT_AUTHORIZED

            val start = System.currentTimeMillis()
            val state = manager.connect()

            state shouldBe MqttConnectionState.FAILED
            (System.currentTimeMillis() - start) shouldBeLessThanOrEqual CONTRACT_TIMEOUT_MILLIS
            broker.handshakeUris shouldHaveSize 0
        }
    }

    // AC43 (Android)
    // GIVEN a connect that failed because the identity pool could not be reached
    // WHEN the identity pool answers again and connect() is called (the device list's retry)
    // THEN it connects
    test("testStory2_androidConnectAfterTheCognitoFailureClearsConnects") {
        withBrokerAndClient {
            pool.answer = IdentityPoolAnswer.NETWORK_ERROR
            manager.connect() shouldBe MqttConnectionState.FAILED

            pool.answer = IdentityPoolAnswer.CREDENTIALS
            manager.connect() shouldBe MqttConnectionState.CONNECTED

            broker.connects shouldHaveSize 1
        }
    }

    // AC44 (Android)
    // GIVEN a release configuration and STORY-1's session SignedOut
    // WHEN connect() is called
    // THEN it returns FAILED, makes no Cognito identity pool call and opens no socket
    test("testStory2_androidSignedOutConnectFailsWithoutCognitoCallOrSocket") {
        withBrokerAndClient(tokens = null) {
            manager.connect() shouldBe MqttConnectionState.FAILED

            pool.totalCalls shouldBe 0
            broker.handshakeUris shouldHaveSize 0
        }
    }

    // AC44 (Android)
    // GIVEN the device list connected, then the user signs out while it is open
    // WHEN connect() is called
    // THEN it returns FAILED, the old identity's connection is closed and not opened again
    test("testStory2_androidConnectAfterSignOutWhileConnectedFailsWithoutANewAttempt") {
        withBrokerAndClient {
            manager.connect() shouldBe MqttConnectionState.CONNECTED

            tokenStore.replaceWith(null)
            awaitUntil("the old identity's connection is closed") { broker.connectedClientCount == 0 }
            val state = manager.connect()
            delay(FAST_RECONNECT_MILLIS * 5)

            state shouldBe MqttConnectionState.FAILED
            broker.connects shouldHaveSize 1
        }
    }

    // AC45 (Android)
    // GIVEN Cognito identity ID eu-north-1:1b2c3d4e-...-000000000001 and install suffix testA
    // WHEN the release client connects
    // THEN its client ID is eu-north-1:1b2c3d4e-0000-4000-8000-000000000001-testA
    test("testStory2_androidClientIdIsTheIdentityIdThenTheInstallSuffix") {
        withBrokerAndClient {
            manager.connect() shouldBe MqttConnectionState.CONNECTED

            broker.connects shouldBe listOf("$IDENTITY_A-testA")
            manager.getClientId() shouldBe "$IDENTITY_A-testA"
        }
    }

    // AC45 (Android)
    // GIVEN the release client connected as <identity A>-testA
    // WHEN the control screen requests the status of device pico1 and subscribes to its reply
    // THEN the request is {"applicationId":"<identity A>-testA"} on devices/pico1/status, and the reply is subscribed
    //      on application/<identity A>-testA/pico1/status
    test("testStory2_androidStatusRequestAndReplyTopicUseTheIdentityClientId") {
        withBrokerAndClient {
            manager.connect() shouldBe MqttConnectionState.CONNECTED
            val reply = scope.launch { SubscribeSystemStatusTopic(manager).subscribeState("pico1").collect {} }
            awaitUntil("the reply topic is subscribed") { broker.subscriptions.isNotEmpty() }

            AwsSystemStateRequest(manager).requestSystemState("pico1")
            reply.cancel()

            broker.published shouldBe listOf(
                FakeAwsIotBroker.Published("devices/pico1/status", """{"applicationId":"$IDENTITY_A-testA"}"""),
            )
            broker.subscriptions shouldContain "application/$IDENTITY_A-testA/pico1/status"
        }
    }

    // AC45 (Android)
    // GIVEN the release client connected as account A, then the user signs out and signs in with account B
    // WHEN connect() is called
    // THEN the new connection's client ID starts with account B's identity ID
    test("testStory2_androidSignInWithAnotherAccountChangesTheClientId") {
        withBrokerAndClient {
            pool.identities["eyJtest.accountB.sig"] = IDENTITY_B
            manager.connect() shouldBe MqttConnectionState.CONNECTED

            tokenStore.replaceWith(null)
            awaitUntil("account A's connection is closed") { broker.connectedClientCount == 0 }
            tokenStore.replaceWith(tokensOf("eyJtest.accountB.sig"))
            manager.connect() shouldBe MqttConnectionState.CONNECTED

            broker.connects.last() shouldBe "$IDENTITY_B-testA"
            manager.getClientId() shouldBe "$IDENTITY_B-testA"
        }
    }

    // AC46 (Android, the client's part; the device list's part is in core/presentation)
    // GIVEN a signed-in session whose credentials are issued
    // WHEN AWS IoT closes the connection after the CONNECT, before any CONNACK (no AWS IoT policy)
    // THEN the connect source fails with IotAccessDeniedException, within 10 s
    test("testStory2_androidConnectionClosedBeforeConnackIsNoIotAccess") {
        withBrokerAndClient {
            broker.mode = FakeAwsIotBroker.Mode.HANG_UP_AFTER_CONNECT

            val start = System.currentTimeMillis()
            shouldThrow<IotAccessDeniedException> { ConnectMqttSource(manager).connect() }
            (System.currentTimeMillis() - start) shouldBeLessThanOrEqual CONTRACT_TIMEOUT_MILLIS
        }
    }

    // AC46 (Android, the client's part)
    // GIVEN a signed-in session whose credentials are issued
    // WHEN AWS IoT answers the WebSocket upgrade with HTTP 403
    // THEN the connect source fails with IotAccessDeniedException
    test("testStory2_androidUpgradeAnsweredWith403IsNoIotAccess") {
        withBrokerAndClient {
            broker.mode = FakeAwsIotBroker.Mode.REJECT_UPGRADE

            shouldThrow<IotAccessDeniedException> { ConnectMqttSource(manager).connect() }
        }
    }

    // AC46 (Android, the client's part)
    // GIVEN a signed-in session whose credentials are issued, and nothing listening at the endpoint (no answer)
    // WHEN the client connects
    // THEN the connect source fails, and not with IotAccessDeniedException
    test("testStory2_androidSocketErrorBeforeAnyAnswerIsNotNoIotAccess") {
        withBrokerAndClient {
            broker.stop()

            val failure = runCatching { ConnectMqttSource(manager).connect() }.exceptionOrNull()

            failure shouldNotBe null
            failure.shouldNotBeInstanceOf<IotAccessDeniedException>()
        }
    }

    // AC47 (Android)
    // GIVEN AWS IoT refused the connection (no access) and automatic reconnects 100 ms apart
    // WHEN 3 s pass without any connect() call
    // THEN the client made no further connection attempt by itself
    test("testStory2_androidNoAutomaticReconnectAfterARefusal") {
        withBrokerAndClient {
            broker.mode = FakeAwsIotBroker.Mode.HANG_UP_AFTER_CONNECT
            manager.connect() shouldNotBe MqttConnectionState.CONNECTED

            delay(3_000)

            broker.handshakeUris shouldHaveSize 1
            broker.connects shouldHaveSize 1
        }
    }

    // AC47 (Android)
    // GIVEN AWS IoT refused the connection at 12:00:00Z
    // WHEN connect() is called at 12:01:00Z ("Search again", or the return to the foreground) and again at 12:02:00Z
    // THEN each call makes exactly one new attempt, with new credentials and a newly signed URL
    test("testStory2_androidEachConnectAfterARefusalMakesExactlyOneAttemptWithNewCredentials") {
        val keys = listOf(TEST_ACCESS_KEY_ID, "AKIDSECONDKEY", "AKIDTHIRDKEY")
        withBrokerAndClient(accessKeyIds = keys) {
            broker.mode = FakeAwsIotBroker.Mode.HANG_UP_AFTER_CONNECT
            manager.connect() shouldNotBe MqttConnectionState.CONNECTED

            repeat(2) {
                clock.epochSeconds += ONE_MINUTE
                manager.connect() shouldNotBe MqttConnectionState.CONNECTED
            }
            delay(FAST_RECONNECT_MILLIS * 10)

            broker.handshakeUris shouldHaveSize 3
            broker.handshakeUris.map { it.signingDate } shouldBe
                listOf("20261121T120000Z", "20261121T120100Z", "20261121T120200Z")
            broker.handshakeUris.map { it.credentialAccessKeyId } shouldBe keys
        }
    }

    // AC50 (Android)
    // GIVEN the release client connected over WebSocket and a collector of subscribe("devices/+/info")
    // WHEN the broker restarts
    // THEN the same flow keeps emitting without being collected again, and reconnects() emits exactly once
    test("testStory2_androidSubscriptionSurvivesABrokerRestartAndReconnectsEmitsOnce") {
        withBrokerAndClient {
            val infos = CopyOnWriteArrayList<IotMessage>()
            val reconnects = AtomicInteger(0)
            manager.connect() shouldBe MqttConnectionState.CONNECTED
            scope.launch { manager.subscribe("devices/+/info").collect { infos += it } }
            scope.launch { manager.reconnects().collect { reconnects.incrementAndGet() } }
            awaitUntil("devices/+/info is subscribed") { broker.hasSubscriber("devices/+/info") }
            broker.publish("devices/a/info", "before")
            awaitUntil("the first info arrived") { infos.size == 1 }

            broker.stop()
            broker.start()
            awaitUntil("devices/+/info is subscribed again") { broker.hasSubscriber("devices/+/info") }
            broker.publish("devices/b/info", "after")
            awaitUntil("the info after the restart arrived") { infos.size == 2 }
            delay(FAST_RECONNECT_MILLIS * 5)

            infos.map { it.topic to it.payload } shouldBe
                listOf("devices/a/info" to "before", "devices/b/info" to "after")
            reconnects.get() shouldBe 1
            broker.connects shouldHaveSize 2
        }
    }

    // AC50 (Android)
    // GIVEN the release client whose broker restarted and whose automatic reconnect is under way
    // WHEN connect() is called while that reconnect runs
    // THEN it returns CONNECTED, and the broker sees no second CONNECT for that restart
    test("testStory2_androidConnectDuringTheReconnectReturnsConnectedWithoutASecondConnect") {
        withBrokerAndClient(reconnectDelayMillis = 1_000L) {
            manager.connect() shouldBe MqttConnectionState.CONNECTED
            broker.stop()
            broker.start()

            val during = async { manager.connect() }
            val state = during.await()
            delay(1_500)

            state shouldBe MqttConnectionState.CONNECTED
            broker.connects shouldHaveSize 2
        }
    }

    // AC53 (shared and Android code)
    // GIVEN a recording Kermit log writer, the AC37 credentials and the ID token eyJtest.idtoken.sig
    // WHEN the client connects, its connection drops and it reconnects on its own, AWS IoT then refuses it, and the
    //      identity pool fails
    // THEN no recorded message has the access key ID, the secret key, the session token, the ID token or any of
    //      X-Amz-Signature, X-Amz-Security-Token, X-Amz-Credential
    test("testStory2_noCredentialTokenOrSignedUrlReachesTheKermitLog") {
        val recorder = RecordingLogWriter()
        Logger.setLogWriters(recorder)
        Logger.setMinSeverity(Severity.Verbose)
        try {
            withBrokerAndClient {
                manager.connect() shouldBe MqttConnectionState.CONNECTED
                broker.dropConnections()
                awaitUntil("HiveMQ reconnected on its own") { broker.connects.size == 2 }

                broker.mode = FakeAwsIotBroker.Mode.HANG_UP_AFTER_CONNECT
                broker.dropConnections()
                awaitUntil("AWS IoT refused the reconnect") { broker.connects.size == 3 }
                delay(FAST_RECONNECT_MILLIS * 5)

                pool.answer = IdentityPoolAnswer.NETWORK_ERROR
                clock.epochSeconds += 2 * 60 * ONE_MINUTE
                manager.connect() shouldNotBe MqttConnectionState.CONNECTED
            }
        } finally {
            Logger.setLogWriters(platformLogWriter())
        }

        recorder.lines.shouldNotBeEmpty()
        val forbidden = listOf(
            TEST_ACCESS_KEY_ID, TEST_SECRET_KEY, "AQoD", "eyJtest", TEST_ID_TOKEN,
            "X-Amz-Signature", "X-Amz-Security-Token", "X-Amz-Credential",
        )
        recorder.lines.forEach { line -> forbidden.forEach { secret -> line shouldNotContain secret } }
    }

    // AC51 (Android, the data part; the debug settings from BuildConfig are app's Story2BrokerModuleTest)
    // GIVEN the Android data graph with debug settings (plaintext TCP, no TLS) and a signed-in session, and a counting
    //       identity pool bound in the graph
    // WHEN the client connects (to a local TCP listener)
    // THEN it opens a plain TCP connection and makes no Cognito identity pool call
    test("testStory2_debugGraphConnectsOverPlainTcpWithoutCognito") {
        ServerSocket(0).use { listener ->
            val accepted = AtomicInteger(0)
            Thread {
                runCatching {
                    while (true) listener.accept().use { accepted.incrementAndGet() }
                }
            }.apply { isDaemon = true; start() }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val clock = FakeClock()
            val pool = FakeCognitoIdentityPool(clock)
            val tokenStore = InMemoryTokenStore()
            val graph = koinGraph(
                module {
                    single { MqttSettings("debugClientId0123456789", "127.0.0.1", listener.localPort, TlsMode.NONE) }
                    single<TokenStore> { tokenStore }
                    single<AwsIotCredentialsCache> { testCredentialsCache(pool, tokenStore, scope) }
                },
            )
            try {
                scope.launch { graph.koin.get<IotManager>().connect() }
                runBlocking { awaitUntil("the debug client opened its TCP connection") { accepted.get() >= 1 } }

                pool.totalCalls shouldBe 0
            } finally {
                graph.close()
                scope.cancel()
            }
        }
    }
})
