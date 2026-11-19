package lt.setkus.pliuskis.data.di

import lt.setkus.pliuskis.data.IotManager
import lt.setkus.pliuskis.data.mqttclient.NotImplementedIotManager
import org.koin.dsl.module

/**
 * The iOS data bindings, loaded next to the common `dataModule`. iOS has no MQTT client yet, so [IotManager] is
 * [NotImplementedIotManager]. It needs the [lt.setkus.pliuskis.data.mqttclient.MqttSettings] that `initShared` binds.
 */
val iosDataModule = module {
    single<IotManager> { NotImplementedIotManager(get()) }
}
