package lt.setkus.pliuskis.data.di

import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import lt.setkus.pliuskis.core.systemstate.SystemStateRequestable
import lt.setkus.pliuskis.data.BuildConfig
import lt.setkus.pliuskis.data.IotManager
import lt.setkus.pliuskis.data.mqttclient.HiveMqttClientManager
import lt.setkus.pliuskis.data.mqttclient.getDebugMqttClient
import lt.setkus.pliuskis.data.mqttclient.getProductionMqttClient
import lt.setkus.pliuskis.data.systemstate.AwsSystemStateRequest
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataModule = module {
    single<Mqtt3Client> {
        if (BuildConfig.DEBUG) getDebugMqttClient() else getProductionMqttClient(androidContext())
    }

    single<IotManager> {
        HiveMqttClientManager(get())
    }

    factory<SystemStateRequestable> { AwsSystemStateRequest(get()) }
}