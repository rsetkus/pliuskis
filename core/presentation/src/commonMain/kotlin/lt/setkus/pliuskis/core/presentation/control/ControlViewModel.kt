package lt.setkus.pliuskis.core.presentation.control

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
import lt.setkus.pliuskis.core.command.water.ExecuteWaterCommandUseCase
import lt.setkus.pliuskis.core.presentation.control.ControlUiState.Success
import lt.setkus.pliuskis.core.systemstate.GetSystemStatusUseUseCase
import lt.setkus.pliuskis.core.systemstate.RequestSystemStateUseCase
import lt.setkus.pliuskis.core.systemstate.SystemState
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import lt.setkus.pliuskis.core.presentation.control.ControlUiState.Error as ControlError

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * State of the control screen of one device. The caller passes [deviceId]; on Android the navigation layer reads it
 * from the route.
 *
 * Other modules create it with [newControlViewModel]: the constructor takes use cases, which stay out of the Swift API
 * of the `shared` framework.
 */
class ControlViewModel internal constructor(
    private val deviceId: String,
    connect: suspend () -> Either<Throwable, String>,
    systemStatusUseCase: RequestSystemStateUseCase,
    getSystemStatusUseUseCase: GetSystemStatusUseUseCase,
    private val executeCommandUseCase: ExecuteWaterCommandUseCase,
    mapper: (SystemState) -> DeviceStatus,
) : ViewModel() {

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

/**
 * Creates a [ControlViewModel]; Android's Koin module and the `shared` module call it. Hidden from Swift, which uses
 * `createControlViewModel(deviceId)` of the `shared` module instead.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
@Suppress("LongParameterList")
fun newControlViewModel(
    deviceId: String,
    connect: suspend () -> Either<Throwable, String>,
    systemStatusUseCase: RequestSystemStateUseCase,
    getSystemStatusUseUseCase: GetSystemStatusUseUseCase,
    executeCommandUseCase: ExecuteWaterCommandUseCase,
    mapper: (SystemState) -> DeviceStatus,
): ControlViewModel = ControlViewModel(
    deviceId = deviceId,
    connect = connect,
    systemStatusUseCase = systemStatusUseCase,
    getSystemStatusUseUseCase = getSystemStatusUseUseCase,
    executeCommandUseCase = executeCommandUseCase,
    mapper = mapper,
)

sealed interface ControlUiState {
    data object Loading : ControlUiState
    data class Error(val message: String) : ControlUiState
    data class Success(val data: DeviceStatus) : ControlUiState
}
