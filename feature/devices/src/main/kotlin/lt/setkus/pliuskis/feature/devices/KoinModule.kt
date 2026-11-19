package lt.setkus.pliuskis.feature.devices

import lt.setkus.pliuskis.core.devicelist.Device
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val devicesModule = module {
    viewModel {
        DevicesViewModel(
            connect = get(),
            observeDevices = get(),
            mapper = get(qualifier = named("deviceListItemMapper")),
        )
    }
    single<(Device) -> DeviceListItem>(
        qualifier = named("deviceListItemMapper")
    ) { ::fromDomainModel }
}
