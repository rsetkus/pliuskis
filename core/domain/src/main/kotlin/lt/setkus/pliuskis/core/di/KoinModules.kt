package lt.setkus.pliuskis.core.di

import lt.setkus.pliuskis.core.command.water.ExecuteWaterCommandUseCase
import lt.setkus.pliuskis.core.connect.ConnectUseCase
import lt.setkus.pliuskis.core.devicelist.ObserveDevicesUseCase
import lt.setkus.pliuskis.core.systemstate.GetSystemStatusUseUseCase
import lt.setkus.pliuskis.core.systemstate.RequestSystemStateUseCase
import org.koin.dsl.module

val coreModule = module {
    factory { RequestSystemStateUseCase(get()) }
    factory { ConnectUseCase(get()) }
    factory { ObserveDevicesUseCase(get()) }
    factory { GetSystemStatusUseUseCase(get()) }
    factory { ExecuteWaterCommandUseCase(get()) }
}
