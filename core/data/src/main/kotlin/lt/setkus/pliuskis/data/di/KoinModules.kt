package lt.setkus.pliuskis.data.di

import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import lt.setkus.pliuskis.core.command.CommandExecutable
import lt.setkus.pliuskis.core.connect.Connectable
import lt.setkus.pliuskis.core.devicelist.DeviceSubscribable
import lt.setkus.pliuskis.core.systemstate.SystemStateRequestable
import lt.setkus.pliuskis.core.systemstate.SystemStateSubscribable
import lt.setkus.pliuskis.data.ConnectMqttSource
import lt.setkus.pliuskis.data.IotManager
import lt.setkus.pliuskis.data.clientid.ClientIdStore
import lt.setkus.pliuskis.data.clientid.SharedPreferencesClientIdStore
import lt.setkus.pliuskis.data.command.water.MqttExecuteWaterCommand
import lt.setkus.pliuskis.data.devices.SubscribeDeviceTopics
import lt.setkus.pliuskis.data.mqttclient.HiveMqttClientManager
import lt.setkus.pliuskis.data.mqttclient.createMqttClient
import lt.setkus.pliuskis.data.systemstate.AwsSystemStateRequest
import lt.setkus.pliuskis.data.systemstate.SubscribeSystemStatusTopic
import lt.setkus.pliuskis.data.util.AndroidConnectivityManager
import lt.setkus.pliuskis.data.util.NetworkConnectivity
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataModule = module {
    single<ClientIdStore> { SharedPreferencesClientIdStore(androidContext()) }

    // MqttSettings (and, for TLS, MqttTlsSetup) come from the app module, which reads BuildConfig.
    single<Mqtt3Client> { createMqttClient(get(), getOrNull()) }

    single<IotManager> {
        HiveMqttClientManager(get())
    }

    single<NetworkConnectivity> { AndroidConnectivityManager(androidContext()) }

    factory<SystemStateRequestable> { AwsSystemStateRequest(get()) }
    factory<Connectable> { ConnectMqttSource(get()) }
    factory<DeviceSubscribable> { SubscribeDeviceTopics(get()) }
    factory<SystemStateSubscribable> { SubscribeSystemStatusTopic(get()) }
    factory<CommandExecutable<String>> { MqttExecuteWaterCommand(get()) }
}
