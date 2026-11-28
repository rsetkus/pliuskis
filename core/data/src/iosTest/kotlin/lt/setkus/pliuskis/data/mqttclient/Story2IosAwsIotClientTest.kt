package lt.setkus.pliuskis.data.mqttclient

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldNotBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import lt.setkus.pliuskis.core.auth.AuthTokens
import lt.setkus.pliuskis.core.connect.IotAccessDeniedException
import lt.setkus.pliuskis.data.ConnectMqttSource
import lt.setkus.pliuskis.data.IotManager
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
import lt.setkus.pliuskis.data.aws.credentialAccessKeyId
import lt.setkus.pliuskis.data.aws.signingDate
import lt.setkus.pliuskis.data.aws.testAwsIotSettings
import lt.setkus.pliuskis.data.aws.testCredentialsCache
import lt.setkus.pliuskis.data.aws.tokensOf
import lt.setkus.pliuskis.data.clientid.ClientIdProvider
import lt.setkus.pliuskis.data.clientid.InMemoryClientIdStore
import lt.setkus.pliuskis.data.di.dataModule
import lt.setkus.pliuskis.data.di.iosDataModule
import lt.setkus.pliuskis.data.systemstate.AwsSystemStateRequest
import lt.setkus.pliuskis.data.systemstate.SubscribeSystemStatusTopic
import lt.setkus.pliuskis.data.util.FakeNetworkPathSource
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module

private const val AWS_IOT_PORT = 443
private const val PENDING_CONNECTION_TIMEOUT_MILLIS = 10_000L

/**
 * The iOS release graph over fakes: `iosDataModule` with AWS IoT, the settings `initShared` binds (the AWS IoT
 * endpoint on port 443 and the install suffix as client ID), and the production credentials cache over a fake identity
 * pool, a fake session and a fake clock. The fake transport asks the cache for a newly signed URL on every attempt, as
 * the Swift `CocoaMqttTransport` does through `signedAwsIotWebSocketUrl()`.
 */
private class IosAwsIotHarness(
    scope: TestScope,
    tokens: AuthTokens? = tokensOf(),
    awsIot: AwsIotSettings = testAwsIotSettings(),
    installSuffix: String = INSTALL_SUFFIX,
    accessKeyIds: List<String> = listOf(TEST_ACCESS_KEY_ID),
    backgroundScope: CoroutineScope = scope.backgroundScope,
) {
    val clock = FakeClock()
    val pool = FakeCognitoIdentityPool(
        clock = clock,
        dispatcher = StandardTestDispatcher(scope.testScheduler),
        nextAccessKeyIds = accessKeyIds,
    )
    val tokenStore = InMemoryTokenStore(tokens)
    val authenticator = FakeAuthenticator()
    val credentials = testCredentialsCache(pool, tokenStore, backgroundScope, authenticator, awsIot)
    val transport = FakeWebSocketMqttTransport { credentials.signedUrlNow() }
    private val koin: KoinApplication = koinApplication {
        modules(
            dataModule,
            iosDataModule(transport, FakeNetworkPathSource(), awsIot),
            module {
                single { MqttSettings(installSuffix, awsIot.endpoint, AWS_IOT_PORT, TlsMode.WEBSOCKET_SIGV4) }
                single { credentials }
            },
        )
    }
    val manager: IotManager = koin.koin.get()

    fun close() = koin.close()
}

private inline fun <T> withHarness(harness: IosAwsIotHarness, block: IosAwsIotHarness.() -> T): T =
    try {
        harness.block()
    } finally {
        harness.close()
    }

/** Starts connect() and lets the client prepare and start its attempt; the caller then answers for AWS IoT. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.startConnect(manager: IotManager) = async { manager.connect() }.also { runCurrent() }

/** Connects: AWS IoT accepts the attempt the client starts. */
@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun TestScope.connected(harness: IosAwsIotHarness) {
    val result = startConnect(harness.manager)
    harness.transport.acceptConnection()
    result.await() shouldBe MqttConnectionState.CONNECTED
}

/**
 * STORY-2 on iOS: the release MQTT client (the Kotlin `IosMqttClientManager` over the CocoaMQTT WebSocket transport)
 * connects to AWS IoT with the signed-in identity's Cognito credentials and a newly signed URL on every attempt.
 * AC40, AC41 (iOS), AC43, AC44, AC45, AC46 and AC47 (the client's part), AC36 and AC52 (the Kotlin part).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Story2IosAwsIotClientTest : FunSpec({

    // AC40 (iOS)
    // GIVEN the clock at 12:00:00Z, a first connect, a dropped connection, and the clock at 12:10:00Z
    // WHEN connect() is called again
    // THEN the two attempts used two different URLs, signed at 12:00:00Z and 12:10:00Z
    test("testStory2_iosEveryConnectUsesANewlySignedUrl") {
        runTest {
            withHarness(IosAwsIotHarness(this)) {
                connected(this)
                transport.dropConnection()

                clock.epochSeconds += 10 * ONE_MINUTE
                connected(this)

                transport.attempts shouldHaveSize 2
                transport.attempts.map { it.url?.signingDate } shouldBe listOf("20261121T120000Z", "20261121T121000Z")
                transport.attempts[0].url shouldNotBe transport.attempts[1].url
            }
        }
    }

    // AC41 (iOS)
    // GIVEN a client connected at 12:00:00Z whose connection drops at 12:10:00Z
    // WHEN CocoaMQTT reconnects on its own, without any connect() call
    // THEN the reconnect attempt opens a URL signed at 12:10:00Z, not the first connection's URL
    test("testStory2_iosAutomaticReconnectUsesAUrlSignedAtThatAttempt") {
        runTest {
            withHarness(IosAwsIotHarness(this)) {
                connected(this)
                clock.epochSeconds += 10 * ONE_MINUTE
                transport.dropConnection()

                transport.reconnectOnItsOwn() shouldBe true
                transport.acceptConnection()

                transport.attempts shouldHaveSize 2
                transport.attempts[1].url?.signingDate shouldBe "20261121T121000Z"
                transport.attempts[1].url shouldNotBe transport.attempts[0].url
            }
        }
    }

    // AC43 (iOS)
    // GIVEN a signed-in session and an identity pool that cannot be reached (network error)
    // WHEN connect() is called
    // THEN it returns FAILED within 10 s and the transport is never asked to open a socket
    test("testStory2_iosCognitoNetworkErrorFailsTheConnectWithoutOpeningASocket") {
        runTest {
            withHarness(IosAwsIotHarness(this)) {
                pool.answer = IdentityPoolAnswer.NETWORK_ERROR

                val result = startConnect(manager).await()

                result shouldBe MqttConnectionState.FAILED
                currentTime shouldBeLessThanOrEqual PENDING_CONNECTION_TIMEOUT_MILLIS
                transport.attempts shouldHaveSize 0
            }
        }
    }

    // AC43 (iOS)
    // GIVEN a signed-in session whose ID token Cognito rejects (NotAuthorizedException), also after a token refresh
    // WHEN connect() is called
    // THEN it returns FAILED within 10 s and the transport is never asked to open a socket
    test("testStory2_iosRejectedIdTokenFailsTheConnectWithoutOpeningASocket") {
        runTest {
            withHarness(IosAwsIotHarness(this)) {
                pool.answer = IdentityPoolAnswer.NOT_AUTHORIZED

                val result = startConnect(manager).await()

                result shouldBe MqttConnectionState.FAILED
                currentTime shouldBeLessThanOrEqual PENDING_CONNECTION_TIMEOUT_MILLIS
                transport.attempts shouldHaveSize 0
            }
        }
    }

    // AC43 (iOS)
    // GIVEN a connect that failed because the identity pool could not be reached
    // WHEN the identity pool answers again and the device list retries (connect() again)
    // THEN that attempt opens a signed URL and connects
    test("testStory2_iosConnectAfterTheCognitoFailureClearsConnects") {
        runTest {
            withHarness(IosAwsIotHarness(this)) {
                pool.answer = IdentityPoolAnswer.NETWORK_ERROR
                startConnect(manager).await() shouldBe MqttConnectionState.FAILED

                pool.answer = IdentityPoolAnswer.CREDENTIALS
                connected(this)

                transport.attempts shouldHaveSize 1
                transport.attempts[0].url?.signingDate shouldBe "20261121T120000Z"
            }
        }
    }

    // AC44 (iOS)
    // GIVEN a release configuration and STORY-1's session SignedOut
    // WHEN connect() is called
    // THEN it returns FAILED, makes no Cognito identity pool call and never asks the transport to open a socket
    test("testStory2_iosSignedOutConnectFailsWithoutCognitoCallOrSocket") {
        runTest {
            withHarness(IosAwsIotHarness(this, tokens = null)) {
                val result = startConnect(manager).await()

                result shouldBe MqttConnectionState.FAILED
                pool.totalCalls shouldBe 0
                transport.attempts shouldHaveSize 0
            }
        }
    }

    // AC44 (iOS)
    // GIVEN the device list connected, then the user signs out while it is open
    // WHEN connect() is called
    // THEN it returns FAILED and no attempt is made for the old identity
    test("testStory2_iosConnectAfterSignOutWhileConnectedFailsWithoutANewAttempt") {
        runTest {
            withHarness(IosAwsIotHarness(this)) {
                connected(this)
                val callsBeforeSignOut = pool.totalCalls

                tokenStore.replaceWith(null)
                runCurrent()
                val result = startConnect(manager).await()

                result shouldBe MqttConnectionState.FAILED
                pool.totalCalls shouldBe callsBeforeSignOut
                transport.attempts shouldHaveSize 1
            }
        }
    }

    // AC45 (iOS)
    // GIVEN Cognito identity ID eu-north-1:1b2c3d4e-...-000000000001 and install suffix testA
    // WHEN the release client connects
    // THEN its client ID is eu-north-1:1b2c3d4e-0000-4000-8000-000000000001-testA
    test("testStory2_iosClientIdIsTheIdentityIdThenTheInstallSuffix") {
        runTest {
            withHarness(IosAwsIotHarness(this)) {
                connected(this)

                transport.attempts.single().clientId shouldBe "$IDENTITY_A-testA"
                manager.getClientId() shouldBe "$IDENTITY_A-testA"
            }
        }
    }

    // AC45 (iOS)
    // GIVEN the release client connected as <identity A>-testA
    // WHEN the control screen requests the status of device pico1 and subscribes to its reply
    // THEN the request is {"applicationId":"<identity A>-testA"} on devices/pico1/status, and the reply is subscribed
    //      on application/<identity A>-testA/pico1/status
    test("testStory2_iosStatusRequestAndReplyTopicUseTheIdentityClientId") {
        runTest {
            withHarness(IosAwsIotHarness(this)) {
                connected(this)
                val reply = launch { SubscribeSystemStatusTopic(manager).subscribeState("pico1").collect {} }
                runCurrent()

                val request = async { AwsSystemStateRequest(manager).requestSystemState("pico1") }
                runCurrent()
                val published = transport.publishes.single()
                transport.acknowledgePublish(published.messageId)
                request.await()
                reply.cancel()

                published.topic shouldBe "devices/pico1/status"
                published.payload.decodeToString() shouldBe """{"applicationId":"$IDENTITY_A-testA"}"""
                transport.subscribes shouldContain "application/$IDENTITY_A-testA/pico1/status"
            }
        }
    }

    // AC45 (iOS)
    // GIVEN one install (one client ID store) and two app launches
    // WHEN each launch's release client connects for the same account
    // THEN both use the same client ID
    test("testStory2_iosInstallSuffixStaysTheSameAcrossLaunches") {
        runTest {
            val store = InMemoryClientIdStore()
            val clientIds = (1..2).map {
                val suffix = ClientIdProvider(store).clientId()
                withHarness(IosAwsIotHarness(this, installSuffix = suffix)) {
                    connected(this)
                    transport.attempts.single().clientId
                }
            }

            clientIds[0] shouldBe clientIds[1]
            clientIds[0] shouldBe "$IDENTITY_A-${store.stored}"
        }
    }

    // AC45 (iOS)
    // GIVEN the release client connected as account A, then the user signs out and signs in with account B
    // WHEN connect() is called
    // THEN the new attempt's client ID starts with account B's identity ID
    test("testStory2_iosSignInWithAnotherAccountChangesTheClientId") {
        runTest {
            withHarness(IosAwsIotHarness(this)) {
                pool.identities["eyJtest.accountB.sig"] = IDENTITY_B
                connected(this)

                tokenStore.replaceWith(null)
                runCurrent()
                tokenStore.replaceWith(tokensOf("eyJtest.accountB.sig"))
                runCurrent()
                connected(this)

                transport.attempts.last().clientId shouldBe "$IDENTITY_B-testA"
                manager.getClientId() shouldBe "$IDENTITY_B-testA"
            }
        }
    }

    // AC46 (iOS, the client's part; the device list's part is in core/presentation)
    // GIVEN a signed-in session whose credentials are issued
    // WHEN AWS IoT closes the connection before any CONNACK (the identity has no AWS IoT policy)
    // THEN the connect source fails with IotAccessDeniedException, the "no access" failure
    test("testStory2_iosRefusedConnectionIsNoIotAccess") {
        runTest {
            withHarness(IosAwsIotHarness(this)) {
                val connect = async { runCatching { ConnectMqttSource(manager).connect() } }
                runCurrent()
                transport.refuseConnection()

                shouldThrow<IotAccessDeniedException> { connect.await().getOrThrow() }
            }
        }
    }

    // AC46 (iOS, the client's part)
    // GIVEN a signed-in session whose credentials are issued
    // WHEN the attempt fails with a socket error before any answer from AWS (no network, DNS failure)
    // THEN the connect source fails, and not with IotAccessDeniedException
    test("testStory2_iosSocketErrorBeforeAnyAnswerIsNotNoIotAccess") {
        runTest {
            withHarness(IosAwsIotHarness(this)) {
                val connect = async { runCatching { ConnectMqttSource(manager).connect() } }
                runCurrent()
                transport.failWithSocketError()

                val failure = connect.await().exceptionOrNull()
                failure shouldNotBe null
                failure.shouldNotBeInstanceOf<IotAccessDeniedException>()
            }
        }
    }

    // AC47 (iOS)
    // GIVEN AWS IoT refused the connection (no access)
    // WHEN nothing else happens
    // THEN the client has stopped the transport, so CocoaMQTT makes no automatic reconnect
    test("testStory2_iosNoAutomaticReconnectAfterARefusal") {
        runTest {
            withHarness(IosAwsIotHarness(this)) {
                val result = startConnect(manager)
                transport.refuseConnection()
                result.await() shouldNotBe MqttConnectionState.CONNECTED

                transport.reconnectOnItsOwn() shouldBe false
                transport.attempts shouldHaveSize 1
            }
        }
    }

    // AC47 (iOS)
    // GIVEN AWS IoT refused the connection at 12:00:00Z
    // WHEN connect() is called at 12:01:00Z ("Search again", or the return to the foreground) and again at 12:02:00Z
    // THEN each call makes exactly one new attempt, with new credentials and a newly signed URL
    test("testStory2_iosEachConnectAfterARefusalMakesExactlyOneAttemptWithNewCredentials") {
        runTest {
            val keys = listOf(TEST_ACCESS_KEY_ID, "AKIDSECONDKEY", "AKIDTHIRDKEY")
            withHarness(IosAwsIotHarness(this, accessKeyIds = keys)) {
                val first = startConnect(manager)
                transport.refuseConnection()
                first.await()

                repeat(2) {
                    clock.epochSeconds += ONE_MINUTE
                    val again = startConnect(manager)
                    transport.refuseConnection()
                    again.await()
                }

                transport.attempts shouldHaveSize 3
                transport.attempts.map { it.url?.signingDate } shouldBe
                    listOf("20261121T120000Z", "20261121T120100Z", "20261121T120200Z")
                transport.attempts.map { it.url?.credentialAccessKeyId } shouldBe keys
            }
        }
    }

    // AC36 (iOS, the Kotlin part; the Info.plist mapping is the XCTest Story2ReleaseBrokerConfigurationTests)
    // GIVEN release settings whose AWS IoT endpoint is missing (empty)
    // WHEN connect() is called
    // THEN it returns FAILED and no socket is opened, so the device list shows its error screen
    test("testStory2_iosMissingIotEndpointFailsTheConnect") {
        runTest {
            withHarness(IosAwsIotHarness(this, awsIot = testAwsIotSettings(endpoint = ""))) {
                val result = startConnect(manager).await()

                result shouldBe MqttConnectionState.FAILED
                transport.attempts shouldHaveSize 0
            }
        }
    }

    // AC52 (iOS, the Kotlin part)
    // GIVEN the Debug graph (LAN broker: host localhost, port 1883, plaintext) and a signed-in session
    // WHEN the client connects
    // THEN it connects to localhost:1883 with its own client ID and makes no Cognito identity pool call
    test("testStory2_iosDebugGraphConnectsToTheLanBrokerWithoutCognito") {
        runTest {
            val clock = FakeClock()
            val pool = FakeCognitoIdentityPool(clock, StandardTestDispatcher(testScheduler))
            val credentials = testCredentialsCache(pool, InMemoryTokenStore(), backgroundScope)
            val transport = FakeMqttTransport()
            val koin = koinApplication {
                modules(
                    dataModule,
                    iosDataModule(transport, FakeNetworkPathSource(), awsIot = null),
                    module {
                        single { MqttSettings("debugClientId0123456789", "localhost", 1883, TlsMode.NONE) }
                        single { credentials }
                    },
                )
            }
            try {
                val manager = koin.koin.get<IotManager>()
                val result = startConnect(manager)
                transport.acceptConnection()

                result.await() shouldBe MqttConnectionState.CONNECTED
                transport.connectCalls shouldBe
                    listOf(FakeMqttTransport.ConnectCall("localhost", 1883, "debugClientId0123456789"))
                pool.totalCalls shouldBe 0
            } finally {
                koin.close()
            }
        }
    }
})
