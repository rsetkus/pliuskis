package lt.setkus.feature.control

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import lt.setkus.feature.control.ControlUiState.Error
import lt.setkus.feature.control.ControlUiState.Success
import lt.setkus.feature.control.navigation.ControlArgs
import lt.setkus.pliuskis.core.systemstate.GetSystemStatusUseUseCase
import lt.setkus.pliuskis.core.systemstate.RequestSystemStatelUseCase

class ControlViewModel(
    savedStateHandle: SavedStateHandle,
    systemStatusUseCase: RequestSystemStatelUseCase,
    getSystemStatusUseUseCase: GetSystemStatusUseUseCase
) : ViewModel() {

    private val controlArgs = ControlArgs(savedStateHandle)
    private val deviceId = controlArgs.deviceId

    init {
        systemStatusUseCase(deviceId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    }

    val controlUiState: StateFlow<ControlUiState> = getSystemStatusUseUseCase(deviceId)
        .map {
            it.fold(
                ifRight = { result -> Success(result.toString()) },
                ifLeft = { Error }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ControlUiState.Loading
        )

}

sealed interface ControlUiState {
    data object Loading : ControlUiState
    data object Error : ControlUiState
    data class Success(val data: String) : ControlUiState
}