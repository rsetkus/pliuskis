package lt.setkus.pliuskis.core.presentation.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lt.setkus.pliuskis.core.command.water.ExecuteWaterCommandUseCase
import lt.setkus.pliuskis.core.connect.ObserveReconnectsUseCase
import lt.setkus.pliuskis.core.presentation.control.ControlUiState.Success
import lt.setkus.pliuskis.core.systemstate.GetSystemStatusUseUseCase
import lt.setkus.pliuskis.core.systemstate.RequestSystemStateUseCase
import lt.setkus.pliuskis.core.systemstate.SystemState
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import lt.setkus.pliuskis.core.presentation.control.ControlUiState.Error as ControlError

private const val STOP_TIMEOUT_MILLIS = 5_000L
private const val WATERING_CONFIRMATION_MILLIS = 3_000L

/**
 * State of the control screen of one device. The caller passes [deviceId]; on Android the navigation layer reads it
 * from the route.
 *
 * Device status replies are not retained, so the ViewModel asks for the status once connected, again after every
 * automatic reconnect of the MQTT client while it is alive, again on [refresh], and after a successful [retry]. None
 * of these sends a water command; only [waterPlant] does, and [wateringState] follows it.
 *
 * Other modules create it with [newControlViewModel]: the constructor takes use cases, which stay out of the Swift API
 * of the `shared` framework.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList")
class ControlViewModel internal constructor(
    private val deviceId: String,
    private val connect: suspend () -> Either<Throwable, String>,
    private val systemStatusUseCase: RequestSystemStateUseCase,
    getSystemStatusUseUseCase: GetSystemStatusUseUseCase,
    private val executeCommandUseCase: ExecuteWaterCommandUseCase,
    observeReconnects: ObserveReconnectsUseCase,
    mapper: (SystemState) -> DeviceStatus,
) : ViewModel() {

    /** Bumped by [retry]; each new value connects again. */
    private val connectAttempts = MutableStateFlow(0)

    /**
     * After process death the control screen is restored without the device list, so nothing else
     * has connected the MQTT client. Connecting an already connected client returns at once. Emits `null` while a
     * connect is running and the result when it ends; [retry] starts a new attempt.
     */
    private val connection: SharedFlow<Either<Throwable, String>?> = connectAttempts
        .transformLatest {
            emit(null)
            emit(connect())
        }
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    private val _wateringState = MutableStateFlow(WateringState.IDLE)

    /** Feedback for the water button: sending, started (for a moment), or failed. */
    val wateringState: StateFlow<WateringState> = _wateringState.asStateFlow()

    init {
        viewModelScope.launch {
            connection.collectLatest { result ->
                if (result?.isRight() == true) {
                    requestStatus()
                    observeReconnects(Unit).collect { reconnect ->
                        if (reconnect.isRight()) requestStatus()
                    }
                }
            }
        }
    }

    val controlUiState: StateFlow<ControlUiState> = connection
        .flatMapLatest { result ->
            result?.fold(
                ifLeft = { error -> flowOf(ControlError(error.message ?: "No message")) },
                ifRight = { statusUpdates(getSystemStatusUseUseCase, mapper) }
            ) ?: flowOf(ControlUiState.Loading)
        }
        .stateIn(
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

    /**
     * Sends the water command once. [wateringState] goes to SENDING, then STARTED (back to IDLE after
     * [WATERING_CONFIRMATION_MILLIS]) or FAILED. A tap while a command is still sending is ignored.
     */
    fun waterPlant() {
        if (_wateringState.value != WateringState.SENDING) {
            _wateringState.value = WateringState.SENDING
            viewModelScope.launch {
                val sent = executeCommandUseCase(deviceId).first().isRight()
                _wateringState.value = if (sent) WateringState.STARTED else WateringState.FAILED
                if (sent) {
                    delay(WATERING_CONFIRMATION_MILLIS)
                    _wateringState.compareAndSet(WateringState.STARTED, WateringState.IDLE)
                }
            }
        }
    }

    /** Connects again after a failed connection, then asks for the status. Does nothing in any other state. */
    fun retry() {
        if (controlUiState.value is ControlError) {
            connectAttempts.update { it + 1 }
        }
    }

    /**
     * Asks for the device's status again, for example when the app returns to the foreground. It connects first only
     * if needed: connecting a connected client returns at once, and a running attempt is waited on, not repeated.
     * Never sends a water command.
     */
    fun refresh() {
        viewModelScope.launch {
            if (connect().isRight()) requestStatus()
        }
    }

    private suspend fun requestStatus() {
        systemStatusUseCase(deviceId).collect()
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
    observeReconnects: ObserveReconnectsUseCase,
    mapper: (SystemState) -> DeviceStatus,
): ControlViewModel = ControlViewModel(
    deviceId = deviceId,
    connect = connect,
    systemStatusUseCase = systemStatusUseCase,
    getSystemStatusUseUseCase = getSystemStatusUseUseCase,
    executeCommandUseCase = executeCommandUseCase,
    observeReconnects = observeReconnects,
    mapper = mapper,
)

sealed interface ControlUiState {
    data object Loading : ControlUiState
    data class Error(val message: String) : ControlUiState
    data class Success(val data: DeviceStatus) : ControlUiState
}
