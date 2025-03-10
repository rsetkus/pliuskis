package lt.setkus.pliuskis.feature.devices

import lt.setkus.pliuskis.core.devicelist.DeviceDomainModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val devicesModule = module {
    viewModel { DevicesViewModel(
        get(),
        get(),
        get(qualifier = named("deviceListItemMapper"))
    ) }
    single<(DeviceDomainModel) -> DeviceListItem>(
        qualifier = named("deviceListItemMapper")
    ) { ::fromDomainModel }
}
