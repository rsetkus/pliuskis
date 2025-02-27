package lt.setkus.feature.control

import lt.setkus.pliuskis.core.systemstate.SystemState
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val controlModule = module {
    viewModel { ControlViewModel(get(), get(), get(), get(), get()) }
    single<(SystemState) -> DeviceStatus> { DeviceStatusMapper() }
}