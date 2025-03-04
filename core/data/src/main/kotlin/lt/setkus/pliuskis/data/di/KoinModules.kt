package lt.setkus.pliuskis.data.di

import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import lt.setkus.pliuskis.core.command.CommandExecutable
import lt.setkus.pliuskis.core.connect.Connectable
import lt.setkus.pliuskis.core.devicelist.DeviceListPublishable
import lt.setkus.pliuskis.core.devicelist.DeviceSubscribable
import lt.setkus.pliuskis.core.systemstate.SystemStateRequestable
import lt.setkus.pliuskis.core.systemstate.SystemStateSubscribable
import lt.setkus.pliuskis.data.BuildConfig
import lt.setkus.pliuskis.data.ConnectMqttSource
import lt.setkus.pliuskis.data.IotManager
import lt.setkus.pliuskis.data.command.water.MqttExecuteWaterCommand
import lt.setkus.pliuskis.data.devices.PublishDevicesTopic
import lt.setkus.pliuskis.data.devices.SubcribeDeviceTopic
import lt.setkus.pliuskis.data.mqttclient.HiveMqttClientManager
import lt.setkus.pliuskis.data.mqttclient.getDebugMqttClient
import lt.setkus.pliuskis.data.mqttclient.getProductionMqttClient
import lt.setkus.pliuskis.data.systemstate.AwsSystemStateRequest
import lt.setkus.pliuskis.data.systemstate.SubscribeSystemStatusTopic
import lt.setkus.pliuskis.data.util.AndroidConnectivityManager
import lt.setkus.pliuskis.data.util.NetworkConnectivity
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataModule = module {
    single<Mqtt3Client> {
        if (BuildConfig.DEBUG) getDebugMqttClient() else getProductionMqttClient(androidContext())
    }

    single<IotManager> {
        HiveMqttClientManager(get())
    }

    single<NetworkConnectivity> { AndroidConnectivityManager(androidContext()) }

    factory<SystemStateRequestable> { AwsSystemStateRequest(get()) }
    factory<Connectable> { ConnectMqttSource(get()) }
    factory<DeviceListPublishable> { PublishDevicesTopic(get()) }
    factory<DeviceSubscribable> { SubcribeDeviceTopic(get()) }
    factory<SystemStateSubscribable> { SubscribeSystemStatusTopic(get()) }
    factory<CommandExecutable<String>> { MqttExecuteWaterCommand(get()) }
}
