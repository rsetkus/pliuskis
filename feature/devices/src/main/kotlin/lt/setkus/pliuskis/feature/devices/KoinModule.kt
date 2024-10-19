package lt.setkus.pliuskis.feature.devices

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val devicesModule = module {
    viewModel { DevicesViewModel(get(), get()) }
}