package lt.setkus.feature.control

import kotlinx.coroutines.flow.first
import lt.setkus.pliuskis.core.connect.ConnectUseCase
import lt.setkus.pliuskis.core.systemstate.SystemState
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val controlModule = module {
    viewModel {
        val connectUseCase: ConnectUseCase = get()
        ControlViewModel(
            savedStateHandle = get(),
            // The connect call suspends without blocking, so it is safe on the main thread.
            connect = { connectUseCase(Unit).first() },
            systemStatusUseCase = get(),
            getSystemStatusUseUseCase = get(),
            executeCommandUseCase = get(),
            mapper = get()
        )
    }
    single<(SystemState) -> DeviceStatus> { DeviceStatusMapper() }
}
