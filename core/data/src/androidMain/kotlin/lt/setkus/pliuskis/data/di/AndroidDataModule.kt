package lt.setkus.pliuskis.data.di

import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import lt.setkus.pliuskis.data.IotManager
import lt.setkus.pliuskis.data.clientid.ClientIdStore
import lt.setkus.pliuskis.data.clientid.SharedPreferencesClientIdStore
import lt.setkus.pliuskis.data.mqttclient.HiveMqttClientManager
import lt.setkus.pliuskis.data.mqttclient.createMqttClient
import lt.setkus.pliuskis.data.util.AndroidConnectivityManager
import lt.setkus.pliuskis.data.util.NetworkConnectivity
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * The Android data bindings: the HiveMQ client behind [IotManager], and the Android client ID store and network
 * connectivity. The app loads it next to the common `dataModule`.
 */
val androidDataModule = module {
    single<ClientIdStore> { SharedPreferencesClientIdStore(androidContext()) }

    // MqttSettings (and, for TLS, MqttTlsSetup) come from the app module, which reads BuildConfig.
    single<Mqtt3Client> { createMqttClient(get(), getOrNull()) }

    single<IotManager> {
        HiveMqttClientManager(get())
    }

    single<NetworkConnectivity> { AndroidConnectivityManager(androidContext()) }
}
