package lt.setkus.pliuskis.core.di

import lt.setkus.pliuskis.core.command.water.ExecuteWaterCommandUseCase
import lt.setkus.pliuskis.core.connect.ConnectUseCase
import lt.setkus.pliuskis.core.devicelist.GetDeviceUseCase
import lt.setkus.pliuskis.core.devicelist.RequestDeviceListUseCase
import lt.setkus.pliuskis.core.systemstate.GetSystemStatusUseUseCase
import lt.setkus.pliuskis.core.systemstate.RequestSystemStateUseCase
import org.koin.dsl.module

val coreModule = module {
    factory { RequestSystemStateUseCase(get()) }
    factory { ConnectUseCase(get()) }
    factory { RequestDeviceListUseCase(get()) }
    factory { GetDeviceUseCase(get()) }
    factory { GetSystemStatusUseUseCase(get()) }
    factory { ExecuteWaterCommandUseCase(get()) }
}
