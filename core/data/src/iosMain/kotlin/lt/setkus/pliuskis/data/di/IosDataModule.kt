package lt.setkus.pliuskis.data.di

import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.Dispatchers
import lt.setkus.pliuskis.core.auth.Authenticator
import lt.setkus.pliuskis.core.auth.TokenStore
import lt.setkus.pliuskis.data.IotManager
import lt.setkus.pliuskis.data.auth.AuthorizationBrowser
import lt.setkus.pliuskis.data.auth.CognitoAuthenticator
import lt.setkus.pliuskis.data.auth.KeychainSessionStorage
import lt.setkus.pliuskis.data.auth.StoredSessionTokenStore
import lt.setkus.pliuskis.data.auth.WebAuthenticationBrowser
import lt.setkus.pliuskis.data.auth.cognitoHttpClient
import lt.setkus.pliuskis.data.mqttclient.IosMqttClientManager
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
 */
fun iosDataModule(
    transport: MqttTransport,
    pathSource: NetworkPathSource = NwPathMonitorPathSource(),
): Module = module {
    single<IotManager> { IosMqttClientManager(transport, get()) }
    single<NetworkConnectivity> { IosNetworkConnectivity(pathSource) }
    single<TokenStore> { StoredSessionTokenStore(KeychainSessionStorage(), Dispatchers.Default) }
    single<AuthorizationBrowser> { WebAuthenticationBrowser() }
    single<Authenticator> { CognitoAuthenticator(get(), get(), cognitoHttpClient(Darwin)) }
}
