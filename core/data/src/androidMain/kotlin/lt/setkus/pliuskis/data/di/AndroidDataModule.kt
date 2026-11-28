package lt.setkus.pliuskis.data.di

import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import lt.setkus.pliuskis.core.auth.AppleAuthorization
import lt.setkus.pliuskis.core.auth.AppleTokenRevoker
import lt.setkus.pliuskis.core.auth.Authenticator
import lt.setkus.pliuskis.core.auth.TokenStore
import lt.setkus.pliuskis.data.IotManager
import lt.setkus.pliuskis.data.auth.AppleRevokeClient
import lt.setkus.pliuskis.data.auth.AppleWebAuthorization
import lt.setkus.pliuskis.data.auth.AuthorizationBrowser
import lt.setkus.pliuskis.data.auth.CognitoAuthenticator
import lt.setkus.pliuskis.data.auth.CustomTabsAuthorizationBrowser
import lt.setkus.pliuskis.data.auth.KeystoreSessionStorage
import lt.setkus.pliuskis.data.auth.LambdaAppleTokenRevoker
import lt.setkus.pliuskis.data.auth.SignInSettings
import lt.setkus.pliuskis.data.auth.StoredSessionTokenStore
import lt.setkus.pliuskis.data.auth.cognitoHttpClient
import lt.setkus.pliuskis.data.aws.AwsIotCredentialsCache
import lt.setkus.pliuskis.data.aws.CognitoIdentityClient
import lt.setkus.pliuskis.data.clientid.ClientIdStore
import lt.setkus.pliuskis.data.clientid.SharedPreferencesClientIdStore
import lt.setkus.pliuskis.data.mqttclient.AwsIotMqttClientManager
import lt.setkus.pliuskis.data.mqttclient.AwsIotWebSocketTarget
import lt.setkus.pliuskis.data.mqttclient.HiveMqttClientManager
import lt.setkus.pliuskis.data.mqttclient.MqttSettings
import lt.setkus.pliuskis.data.mqttclient.TlsMode
import lt.setkus.pliuskis.data.mqttclient.createMqttClient
import lt.setkus.pliuskis.data.util.AndroidConnectivityManager
import lt.setkus.pliuskis.data.util.NetworkConnectivity
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * The Android data bindings: the HiveMQ client behind [IotManager], the Android client ID store and network
 * connectivity, and the Cognito sign-in ([Authenticator] in a Custom Tab, [TokenStore] encrypted with an Android
 * Keystore key, and the Apple revoke of account deletion). The app loads it next to the common `dataModule`.
 *
 * The [MqttSettings]' TLS mode picks the MQTT client: [TlsMode.WEBSOCKET_SIGV4] (release) is AWS IoT over Cognito,
 * the signed-in identity's temporary credentials ([AwsIotCredentialsCache], with the AwsIotSettings that the app
 * declares) and a newly signed WebSocket URL for every attempt; it never asks for an MqttTlsSetup. The other modes
 * (debug: plaintext TCP to the LAN broker) are the single HiveMQ client built from the settings, with no Cognito call.
 */
val androidDataModule = module {
    single<ClientIdStore> { SharedPreferencesClientIdStore(androidContext()) }

    // MqttSettings (and, for TLS, MqttTlsSetup) come from the app module, which reads BuildConfig.
    single<Mqtt3Client> { createMqttClient(get(), getOrNull()) }

    single<IotManager> {
        val settings: MqttSettings = get()
        if (settings.tlsMode == TlsMode.WEBSOCKET_SIGV4) {
            AwsIotMqttClientManager(
                settings = settings,
                credentials = get(),
                target = get(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            )
        } else {
            HiveMqttClientManager(get())
        }
    }

    // AWS IoT over Cognito (release): the endpoint of the settings over TLS, and the signed-in identity's credentials.
    single { AwsIotWebSocketTarget.of(get()) }
    single {
        AwsIotCredentialsCache(
            settings = get(),
            identity = CognitoIdentityClient(get(), get<SignInSettings>().userPoolId, cognitoHttpClient(Android)),
            tokenStore = get(),
            authenticator = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }

    single<NetworkConnectivity> { AndroidConnectivityManager(androidContext()) }

    // SignInSettings (the dev or prod stack's managed login) come from the app module, which reads BuildConfig.
    single<TokenStore> { StoredSessionTokenStore(KeystoreSessionStorage(androidContext()), Dispatchers.IO) }
    single<AuthorizationBrowser> { CustomTabsAuthorizationBrowser(androidContext()) }
    single<Authenticator> { CognitoAuthenticator(get(), get(), cognitoHttpClient(Android)) }

    // Apple revoke on account deletion: Apple's page in the Custom Tab with the stack's Services ID, then the
    // backend's revoke endpoint. AppleRevokeSettings come from the app module, which reads BuildConfig.
    single<AppleAuthorization> { AppleWebAuthorization(get(), get(), get()) }
    single<AppleTokenRevoker> {
        LambdaAppleTokenRevoker(get(), AppleRevokeClient.SERVICES_ID, cognitoHttpClient(Android))
    }
}
