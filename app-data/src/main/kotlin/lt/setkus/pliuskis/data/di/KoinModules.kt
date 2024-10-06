package lt.setkus.pliuskis.data.di

import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
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
    single<AWSIotMqttManager> {
        AWSIotMqttManager("pliuskis", BuildConfig.AMAZON_IOT_ENDPOINT)
    }

    single<IotManager> {
        val client = if (BuildConfig.DEBUG) getDebugMqttClient() else getProductionMqttClient(androidContext())
        HiveMqttClientManager(client)
    }

    factory<SystemStateRequestable> { AwsSystemStateRequest(get()) }
}