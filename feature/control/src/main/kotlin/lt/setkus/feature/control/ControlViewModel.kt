package lt.setkus.feature.control

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import lt.setkus.feature.control.ControlUiState.Error as ControlError
import lt.setkus.feature.control.ControlUiState.Success
import lt.setkus.feature.control.navigation.ControlArgs
import lt.setkus.pliuskis.core.command.water.ExecuteWaterCommandUseCase
import lt.setkus.pliuskis.core.systemstate.GetSystemStatusUseUseCase
import lt.setkus.pliuskis.core.systemstate.RequestSystemStateUseCase
import lt.setkus.pliuskis.core.systemstate.SystemState

private const val STOP_TIMEOUT_MILLIS = 5_000L

class ControlViewModel(
    savedStateHandle: SavedStateHandle,
    systemStatusUseCase: RequestSystemStateUseCase,
    getSystemStatusUseUseCase: GetSystemStatusUseUseCase,
    private val executeCommandUseCase: ExecuteWaterCommandUseCase,
    mapper: (SystemState) -> DeviceStatus
) : ViewModel() {

    private val controlArgs = ControlArgs(savedStateHandle)
    private val deviceId = controlArgs.deviceId

    init {
        systemStatusUseCase(deviceId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    }

    val controlUiState: StateFlow<ControlUiState> = getSystemStatusUseUseCase(deviceId)
        .map {
            it.fold(
                ifRight = { result -> Success(mapper(result)) },
                ifLeft = { error -> ControlError(error.message ?: "No message") }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ControlUiState.Loading
        )

    fun waterPlant() {
        executeCommandUseCase(deviceId).stateIn(viewModelScope, SharingStarted.Eagerly, Unit)
    }
}

sealed interface ControlUiState {
    data object Loading : ControlUiState
    data class Error(val message: String) : ControlUiState
    data class Success(val data: DeviceStatus) : ControlUiState
}
