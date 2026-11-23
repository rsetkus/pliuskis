package lt.setkus.pliuskis.data.di

import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import lt.setkus.pliuskis.core.auth.AppleAuthorization
import lt.setkus.pliuskis.core.auth.AppleTokenRevoker
import lt.setkus.pliuskis.core.auth.Authenticator
import lt.setkus.pliuskis.core.auth.TokenStore
import lt.setkus.pliuskis.data.IotManager
import lt.setkus.pliuskis.data.auth.AppleRevokeClient
import lt.setkus.pliuskis.data.auth.AuthorizationBrowser
import lt.setkus.pliuskis.data.auth.CognitoAuthenticator
import lt.setkus.pliuskis.data.auth.KeychainSessionStorage
import lt.setkus.pliuskis.data.auth.LambdaAppleTokenRevoker
import lt.setkus.pliuskis.data.auth.NativeAppleAuthorization
import lt.setkus.pliuskis.data.auth.SignInSettings
import lt.setkus.pliuskis.data.auth.StoredSessionTokenStore
import lt.setkus.pliuskis.data.auth.WebAuthenticationBrowser
import lt.setkus.pliuskis.data.auth.cognitoHttpClient
import lt.setkus.pliuskis.data.aws.AwsIotCredentialsCache
import lt.setkus.pliuskis.data.aws.AwsIotSettings
import lt.setkus.pliuskis.data.aws.CognitoIdentityClient
import lt.setkus.pliuskis.data.mqttclient.IosMqttClientManager
import lt.setkus.pliuskis.data.mqttclient.MqttSettings
import lt.setkus.pliuskis.data.mqttclient.MqttTransport
import lt.setkus.pliuskis.data.util.IosNetworkConnectivity
import lt.setkus.pliuskis.data.util.NetworkConnectivity
import lt.setkus.pliuskis.data.util.NetworkPathSource
import lt.setkus.pliuskis.data.util.NwPathMonitorPathSource
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The iOS data bindings, loaded next to the common `dataModule`. [IotManager] is the Kotlin MQTT client over the Swift
 * CocoaMQTT [transport]; it needs the [lt.setkus.pliuskis.data.mqttclient.MqttSettings] that `initShared` binds.
 * [NetworkConnectivity] follows [pathSource], `NWPathMonitor` unless a test passes another one. The Cognito sign-in is
 * [Authenticator] in an `ASWebAuthenticationSession` and [TokenStore] in the Keychain; it needs the
 * [lt.setkus.pliuskis.data.auth.SignInSettings] that `initShared` binds.
 *
 * With [awsIot], the MQTT client connects to AWS IoT over Cognito: [AwsIotCredentialsCache] holds the signed-in
 * identity's temporary credentials and signs the WebSocket URL of every attempt (the Swift transport asks it), and the
 * client ID of an attempt is `<identity ID>-<install suffix>`, where the install suffix is the client ID of the
 * [MqttSettings]. Without it, the client connects to the settings' broker with their client ID, as before.
 */
fun iosDataModule(
    transport: MqttTransport,
    pathSource: NetworkPathSource = NwPathMonitorPathSource(),
    awsIot: AwsIotSettings? = null,
): Module = module {
    if (awsIot == null) {
        single<IotManager> { IosMqttClientManager(transport, get()) }
    } else {
        single {
            AwsIotCredentialsCache(
                settings = awsIot,
                identity = CognitoIdentityClient(awsIot, get<SignInSettings>().userPoolId, cognitoHttpClient(Darwin)),
                tokenStore = get(),
                authenticator = get(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            )
        }
        single<IotManager> {
            val settings: MqttSettings = get()
            val credentials: AwsIotCredentialsCache = get()
            IosMqttClientManager(
                transport = transport,
                settings = settings,
                clientIdForAttempt = {
                    credentials.prepare()?.let { identityId -> "$identityId-${settings.clientId}" }
                },
            )
        }
    }
    single<NetworkConnectivity> { IosNetworkConnectivity(pathSource) }
    single<TokenStore> { StoredSessionTokenStore(KeychainSessionStorage(), Dispatchers.Default) }
    single<AuthorizationBrowser> { WebAuthenticationBrowser() }
    single<Authenticator> { CognitoAuthenticator(get(), get(), cognitoHttpClient(Darwin)) }
    // Apple revoke on account deletion: the native Sign in with Apple sheet (the App ID's code), then the backend's
    // revoke endpoint; it needs the AppleRevokeSettings that initShared binds.
    single<AppleAuthorization> { NativeAppleAuthorization() }
    single<AppleTokenRevoker> { LambdaAppleTokenRevoker(get(), AppleRevokeClient.APP_ID, cognitoHttpClient(Darwin)) }
}
