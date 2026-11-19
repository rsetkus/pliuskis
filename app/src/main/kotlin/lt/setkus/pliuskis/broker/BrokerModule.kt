package lt.setkus.pliuskis.broker

import lt.setkus.pliuskis.BuildConfig
import lt.setkus.pliuskis.data.clientid.ClientIdProvider
import lt.setkus.pliuskis.data.mqttclient.MqttSettings
import lt.setkus.pliuskis.data.mqttclient.MqttTlsSetup
import lt.setkus.pliuskis.data.mqttclient.TlsMode
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private const val DEBUG_BROKER_PORT = 1883
private const val AWS_IOT_PORT = 8883

/**
 * Broker settings for the data layer. The debug build talks to the local broker at DEBUG_BROKER_HOST without TLS; the
 * release build talks to production AWS IoT with the client certificate from the app's keystore asset. Both values
 * come from BuildConfig, which the secrets plugin fills from secrets.properties.
 */
val brokerModule = module {
    single<MqttSettings> {
        val clientId = ClientIdProvider(get()).clientId()
        if (BuildConfig.DEBUG) {
            MqttSettings(clientId, BuildConfig.DEBUG_BROKER_HOST, DEBUG_BROKER_PORT, TlsMode.NONE)
        } else {
            MqttSettings(clientId, BuildConfig.AMAZON_IOT_ENDPOINT, AWS_IOT_PORT, TlsMode.CLIENT_CERTIFICATE)
        }
    }

    if (!BuildConfig.DEBUG) {
        single<MqttTlsSetup> { AssetKeystoreTlsSetup(androidContext().assets, BuildConfig.KEYSTORE_PASSWORD) }
    }
}
