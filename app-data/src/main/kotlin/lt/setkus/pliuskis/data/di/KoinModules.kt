package lt.setkus.pliuskis.data.di

import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import lt.setkus.pliuskis.core.systemstate.SystemStateRequestable
import lt.setkus.pliuskis.data.BuildConfig
import lt.setkus.pliuskis.data.IotManager
import lt.setkus.pliuskis.data.aws.AwsIotManager
import lt.setkus.pliuskis.data.crypto.AwsKeyStoreProducer
import lt.setkus.pliuskis.data.crypto.KeyStoreProducer
import lt.setkus.pliuskis.data.systemstate.AwsSystemStateRequest
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataModule = module {
    single<AWSIotMqttManager> {
        AWSIotMqttManager("pliuskis", BuildConfig.AMAZON_IOT_ENDPOINT)
    }

    single<KeyStoreProducer> {
        AwsKeyStoreProducer(androidContext())
    }

    single<IotManager> {
        AwsIotManager(get(), get())
    }

    factory<SystemStateRequestable> { AwsSystemStateRequest(get()) }
}