package lt.setkus.pliuskis.data.di

import lt.setkus.pliuskis.data.IotManager
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
 * [NetworkConnectivity] follows [pathSource], `NWPathMonitor` unless a test passes another one.
 */
fun iosDataModule(
    transport: MqttTransport,
    pathSource: NetworkPathSource = NwPathMonitorPathSource(),
): Module = module {
    single<IotManager> { IosMqttClientManager(transport, get()) }
    single<NetworkConnectivity> { IosNetworkConnectivity(pathSource) }
}
