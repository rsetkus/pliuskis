package lt.setkus.feature.control

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import lt.setkus.pliuskis.core.connect.ConnectUseCase
import lt.setkus.pliuskis.core.systemstate.SystemState
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val controlModule = module {
    viewModel {
        val connectUseCase: ConnectUseCase = get()
        ControlViewModel(
            savedStateHandle = get(),
            // The HiveMQ connect call blocks, so it runs on the IO dispatcher.
            connect = { connectUseCase(Unit).flowOn(Dispatchers.IO).first() },
            systemStatusUseCase = get(),
            getSystemStatusUseUseCase = get(),
            executeCommandUseCase = get(),
            mapper = get()
        )
    }
    single<(SystemState) -> DeviceStatus> { DeviceStatusMapper() }
}
