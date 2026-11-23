package lt.setkus.pliuskis.data.di

import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.Dispatchers
import lt.setkus.pliuskis.core.auth.Authenticator
import lt.setkus.pliuskis.core.auth.TokenStore
import lt.setkus.pliuskis.data.IotManager
import lt.setkus.pliuskis.data.auth.AuthorizationBrowser
import lt.setkus.pliuskis.data.auth.CognitoAuthenticator
import lt.setkus.pliuskis.data.auth.CustomTabsAuthorizationBrowser
import lt.setkus.pliuskis.data.auth.KeystoreSessionStorage
import lt.setkus.pliuskis.data.auth.StoredSessionTokenStore
import lt.setkus.pliuskis.data.auth.cognitoHttpClient
import lt.setkus.pliuskis.data.clientid.ClientIdStore
import lt.setkus.pliuskis.data.clientid.SharedPreferencesClientIdStore
import lt.setkus.pliuskis.data.mqttclient.HiveMqttClientManager
import lt.setkus.pliuskis.data.mqttclient.createMqttClient
import lt.setkus.pliuskis.data.util.AndroidConnectivityManager
import lt.setkus.pliuskis.data.util.NetworkConnectivity
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * The Android data bindings: the HiveMQ client behind [IotManager], the Android client ID store and network
 * connectivity, and the Cognito sign-in ([Authenticator] in a Custom Tab, [TokenStore] encrypted with an Android
 * Keystore key). The app loads it next to the common `dataModule`.
 */
val androidDataModule = module {
    single<ClientIdStore> { SharedPreferencesClientIdStore(androidContext()) }

    // MqttSettings (and, for TLS, MqttTlsSetup) come from the app module, which reads BuildConfig.
    single<Mqtt3Client> { createMqttClient(get(), getOrNull()) }

    single<IotManager> {
        HiveMqttClientManager(get())
    }

    single<NetworkConnectivity> { AndroidConnectivityManager(androidContext()) }

    // SignInSettings (the dev or prod stack's managed login) come from the app module, which reads BuildConfig.
    single<TokenStore> { StoredSessionTokenStore(KeystoreSessionStorage(androidContext()), Dispatchers.IO) }
    single<AuthorizationBrowser> { CustomTabsAuthorizationBrowser(androidContext()) }
    single<Authenticator> { CognitoAuthenticator(get(), get(), cognitoHttpClient(Android)) }
}
