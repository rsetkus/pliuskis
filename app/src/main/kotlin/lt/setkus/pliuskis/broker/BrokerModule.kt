package lt.setkus.pliuskis.broker

import lt.setkus.pliuskis.BuildConfig
import lt.setkus.pliuskis.data.aws.AwsIotSettings
import lt.setkus.pliuskis.data.aws.SecurityTokenPlacement
import lt.setkus.pliuskis.data.clientid.ClientIdProvider
import lt.setkus.pliuskis.data.mqttclient.MqttSettings
import lt.setkus.pliuskis.data.mqttclient.TlsMode
import org.koin.core.module.Module
import org.koin.dsl.module

private const val DEBUG_BROKER_PORT = 1883
private const val AWS_IOT_WEBSOCKET_PORT = 443

/**
 * The broker values of one build, from BuildConfig, which the secrets plugin fills from secrets.properties (or
 * default.secrets.properties, dummy values only).
 *
 * @property debug whether this is the debug build
 * @property debugBrokerHost the LAN broker of the debug build (`DEBUG_BROKER_HOST`)
 * @property iotEndpoint the prod stack's AWS IoT endpoint (`AMAZON_IOT_ENDPOINT`)
 * @property identityPoolId the prod stack's Cognito identity pool (`COGNITO_IDENTITY_POOL_ID`)
 * @property region the prod stack's region (`AWS_REGION`)
 */
internal data class BrokerBuildConfig(
    val debug: Boolean,
    val debugBrokerHost: String,
    val iotEndpoint: String,
    val identityPoolId: String,
    val region: String,
) {
    companion object {
        fun fromBuildConfig(): BrokerBuildConfig = BrokerBuildConfig(
            debug = BuildConfig.DEBUG,
            debugBrokerHost = BuildConfig.DEBUG_BROKER_HOST,
            iotEndpoint = BuildConfig.AMAZON_IOT_ENDPOINT,
            identityPoolId = BuildConfig.COGNITO_IDENTITY_POOL_ID,
            region = BuildConfig.AWS_REGION,
        )
    }
}

/**
 * Broker settings for the data layer. The debug build talks to the LAN broker at DEBUG_BROKER_HOST, plaintext on port
 * 1883, with this install's 23-character client ID; it needs no AWS credentials. The release build talks to the prod
 * stack's AWS IoT over a SigV4-signed WebSocket (`wss://<endpoint>:443/mqtt`) with the signed-in user's Cognito
 * identity: the settings carry the endpoint and the install suffix of the client ID `<identity ID>-<install suffix>`,
 * and [AwsIotSettings] the identity pool and region. No client certificate (MqttTlsSetup) is declared.
 */
internal fun brokerModule(build: BrokerBuildConfig): Module = module {
    single<MqttSettings> {
        val clientId = ClientIdProvider(get()).clientId()
        if (build.debug) {
            MqttSettings(clientId, build.debugBrokerHost, DEBUG_BROKER_PORT, TlsMode.NONE)
        } else {
            MqttSettings(clientId, build.iotEndpoint, AWS_IOT_WEBSOCKET_PORT, TlsMode.WEBSOCKET_SIGV4)
        }
    }

    if (!build.debug) {
        single {
            AwsIotSettings(
                identityPoolId = build.identityPoolId,
                region = build.region,
                endpoint = build.iotEndpoint,
                // STORY-4 found that AWS IoT accepts the session token only appended after the signature.
                tokenPlacement = SecurityTokenPlacement.APPENDED,
            )
        }
    }
}

/** The broker module of this build. */
val brokerModule: Module = brokerModule(BrokerBuildConfig.fromBuildConfig())
