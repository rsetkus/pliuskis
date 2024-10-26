package lt.setkus.pliuskis.core.di

import lt.setkus.pliuskis.core.connect.ConnectUseCase
import lt.setkus.pliuskis.core.devicelist.GetDeviceUseCase
import lt.setkus.pliuskis.core.devicelist.RequestDeviceListUseCase
import lt.setkus.pliuskis.core.systemstate.GetSystemStatusUseUseCase
import lt.setkus.pliuskis.core.systemstate.RequestSystemStatelUseCase
import org.koin.dsl.module

val coreModule = module {
    factory { RequestSystemStatelUseCase(get()) }
    factory { ConnectUseCase(get()) }
    factory { RequestDeviceListUseCase(get()) }
    factory { GetDeviceUseCase(get()) }
    factory { GetSystemStatusUseUseCase(get()) }
}