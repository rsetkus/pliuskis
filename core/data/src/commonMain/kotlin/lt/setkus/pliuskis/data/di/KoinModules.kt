package lt.setkus.pliuskis.data.di

import lt.setkus.pliuskis.core.command.CommandExecutable
import lt.setkus.pliuskis.core.connect.Connectable
import lt.setkus.pliuskis.core.devicelist.DeviceSubscribable
import lt.setkus.pliuskis.core.systemstate.SystemStateRequestable
import lt.setkus.pliuskis.core.systemstate.SystemStateSubscribable
import lt.setkus.pliuskis.data.ConnectMqttSource
import lt.setkus.pliuskis.data.command.water.MqttExecuteWaterCommand
import lt.setkus.pliuskis.data.devices.SubscribeDeviceTopics
import lt.setkus.pliuskis.data.systemstate.AwsSystemStateRequest
import lt.setkus.pliuskis.data.systemstate.SubscribeSystemStatusTopic
import org.koin.dsl.module

/**
 * The data bindings every platform shares. They need an [lt.setkus.pliuskis.data.IotManager], which the platform data
 * module provides (`androidDataModule` on Android).
 */
val dataModule = module {
    factory<SystemStateRequestable> { AwsSystemStateRequest(get()) }
    factory<Connectable> { ConnectMqttSource(get()) }
    factory<DeviceSubscribable> { SubscribeDeviceTopics(get()) }
    factory<SystemStateSubscribable> { SubscribeSystemStatusTopic(get()) }
    factory<CommandExecutable<String>> { MqttExecuteWaterCommand(get()) }
}
