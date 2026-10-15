package lt.setkus.feature.control

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    connect: suspend () -> Either<Throwable, String>,
    systemStatusUseCase: RequestSystemStateUseCase,
    getSystemStatusUseUseCase: GetSystemStatusUseUseCase,
    private val executeCommandUseCase: ExecuteWaterCommandUseCase,
    mapper: (SystemState) -> DeviceStatus,
) : ViewModel() {

    private val controlArgs = ControlArgs(savedStateHandle)
    private val deviceId = controlArgs.deviceId

    /**
     * After process death the control screen is restored without the device list, so nothing else
     * has connected the MQTT client. Connecting an already connected client returns at once.
     */
    private val connection = viewModelScope.async { connect() }

    init {
        viewModelScope.launch {
            if (connection.await().isRight()) {
                systemStatusUseCase(deviceId).collect()
            }
        }
    }

    val controlUiState: StateFlow<ControlUiState> = flow {
        emitAll(
            connection.await().fold(
                ifLeft = { error -> flowOf(ControlError(error.message ?: "No message")) },
                ifRight = { statusUpdates(getSystemStatusUseUseCase, mapper) }
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ControlUiState.Loading
    )

    private fun statusUpdates(
        getSystemStatusUseUseCase: GetSystemStatusUseUseCase,
        mapper: (SystemState) -> DeviceStatus
    ) = getSystemStatusUseUseCase(deviceId)
        .map { result: Either<Throwable, SystemState> ->
            result.fold(
                ifRight = { state -> Success(mapper(state)) },
                ifLeft = { error -> ControlError(error.message ?: "No message") }
            )
        }

    fun waterPlant() {
        executeCommandUseCase(deviceId).stateIn(viewModelScope, SharingStarted.Eagerly, Unit)
    }
}

sealed interface ControlUiState {
    data object Loading : ControlUiState
    data class Error(val message: String) : ControlUiState
    data class Success(val data: DeviceStatus) : ControlUiState
}
