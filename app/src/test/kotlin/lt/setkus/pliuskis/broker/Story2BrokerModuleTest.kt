package lt.setkus.pliuskis.broker

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import lt.setkus.pliuskis.data.aws.AwsIotSettings
import lt.setkus.pliuskis.data.aws.SecurityTokenPlacement
import lt.setkus.pliuskis.data.clientid.ClientIdStore
import lt.setkus.pliuskis.data.mqttclient.MqttSettings
import lt.setkus.pliuskis.data.mqttclient.MqttTlsSetup
import lt.setkus.pliuskis.data.mqttclient.TlsMode
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module

private const val ENDPOINT = "a1b2c3d4e5f6g7-ats.iot.eu-north-1.amazonaws.com"
private const val IDENTITY_POOL_ID = "eu-north-1:00000000-0000-4000-8000-00000000test"

/** The app install's client ID store, empty at first (a new install). */
private class EmptyClientIdStore : ClientIdStore {
    private var stored: String? = null

    override fun read(): String? = stored

    override fun write(clientId: String) {
        stored = clientId
    }
}

private fun releaseBuild() = BrokerBuildConfig(
    debug = false,
    debugBrokerHost = "10.0.2.2",
    iotEndpoint = ENDPOINT,
    identityPoolId = IDENTITY_POOL_ID,
    region = "eu-north-1",
)

private fun debugBuild() = releaseBuild().copy(debug = true)

private fun <T> withBrokerGraph(build: BrokerBuildConfig, block: (KoinApplication) -> T): T {
    val app = koinApplication {
        modules(brokerModule(build), module { single<ClientIdStore> { EmptyClientIdStore() } })
    }
    return try {
        block(app)
    } finally {
        app.close()
    }
}

/**
 * STORY-2 AC35 and AC51 (the app's part): the broker settings `app` gives the data layer for each build type, from
 * BuildConfig values. The release build connects to AWS IoT over a SigV4-signed WebSocket with the signed-in user's
 * Cognito identity, without the client certificate; the debug build keeps the LAN broker. That the data layer then
 * connects that way (and makes no Cognito call in debug) is core:data's Story2AndroidAwsIotClientTest.
 */
class Story2BrokerModuleTest : FunSpec({

    // AC35
    // GIVEN the release build with IoT endpoint a1b2c3d4e5f6g7-ats.iot.eu-north-1.amazonaws.com and region eu-north-1
    // WHEN the broker settings are built
    // THEN the connection target is wss://<endpoint>:443/mqtt with SigV4 signing
    test("testStory2_releaseSettingsTargetTheEndpointOverASigV4SignedWebSocket") {
        withBrokerGraph(releaseBuild()) { app ->
            val settings = app.koin.get<MqttSettings>()

            settings.connectionTarget shouldBe "wss://a1b2c3d4e5f6g7-ats.iot.eu-north-1.amazonaws.com:443/mqtt"
            settings.tlsMode shouldBe TlsMode.WEBSOCKET_SIGV4
        }
    }

    // AC35
    // GIVEN the release build
    // WHEN the broker module is loaded
    // THEN it gives the AWS IoT connection of the prod stack (identity pool, region, endpoint, the token appended
    //      after signing) and no MqttTlsSetup, so no KeyManagerFactory can be created
    test("testStory2_releaseGraphHasTheCognitoConnectionAndNoClientCertificate") {
        withBrokerGraph(releaseBuild()) { app ->
            val awsIot = app.koin.get<AwsIotSettings>()

            awsIot.identityPoolId shouldBe IDENTITY_POOL_ID
            awsIot.region shouldBe "eu-north-1"
            awsIot.endpoint shouldBe ENDPOINT
            awsIot.tokenPlacement shouldBe SecurityTokenPlacement.APPENDED
            app.koin.getOrNull<MqttTlsSetup>() shouldBe null
        }
    }

    // AC51 (the app's part)
    // GIVEN the debug build with DEBUG_BROKER_HOST 10.0.2.2
    // WHEN the broker settings are built
    // THEN they are host 10.0.2.2, port 1883, plaintext TCP and a 23-character client ID from 0-9a-zA-Z, as today
    test("testStory2_debugSettingsKeepTheLanBroker") {
        withBrokerGraph(debugBuild()) { app ->
            val settings = app.koin.get<MqttSettings>()

            settings.host shouldBe "10.0.2.2"
            settings.port shouldBe 1883
            settings.tlsMode shouldBe TlsMode.NONE
            settings.connectionTarget shouldBe "tcp://10.0.2.2:1883"
            settings.clientId shouldMatch Regex("^[0-9a-zA-Z]{23}$")
        }
    }

    // AC51 (the app's part)
    // GIVEN the debug build
    // WHEN the broker module is loaded
    // THEN it gives no AWS IoT connection over Cognito, so the data layer gets no credentials and signs nothing
    test("testStory2_debugGraphHasNoCognitoConnection") {
        withBrokerGraph(debugBuild()) { app ->
            app.koin.getOrNull<AwsIotSettings>() shouldBe null
        }
    }
})
