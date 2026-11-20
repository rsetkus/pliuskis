package lt.setkus.feature.control

import kotlinx.coroutines.flow.first
import lt.setkus.pliuskis.core.connect.ConnectUseCase
import lt.setkus.pliuskis.core.presentation.control.DeviceStatus
import lt.setkus.pliuskis.core.presentation.control.DeviceStatusMapper
import lt.setkus.pliuskis.core.presentation.control.newControlViewModel
import lt.setkus.pliuskis.core.systemstate.SystemState
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val controlModule = module {
    // The control route passes the device id from its navigation argument: koinViewModel { parametersOf(deviceId) }.
    viewModel { params ->
        val connectUseCase: ConnectUseCase = get()
        newControlViewModel(
            deviceId = params.get(),
            // The connect call suspends without blocking, so it is safe on the main thread.
            connect = { connectUseCase(Unit).first() },
            systemStatusUseCase = get(),
            getSystemStatusUseUseCase = get(),
            executeCommandUseCase = get(),
            observeReconnects = get(),
            mapper = get()
        )
    }
    single<(SystemState) -> DeviceStatus> { DeviceStatusMapper() }
}
