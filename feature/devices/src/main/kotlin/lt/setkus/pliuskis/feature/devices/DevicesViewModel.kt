package lt.setkus.pliuskis.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lt.setkus.pliuskis.core.connect.ConnectUseCase
import lt.setkus.pliuskis.core.devicelist.Device
import lt.setkus.pliuskis.core.devicelist.ObserveDevicesUseCase
import lt.setkus.pliuskis.feature.devices.DevicesListScreenState.Error
import lt.setkus.pliuskis.feature.devices.DevicesListScreenState.Loading
import lt.setkus.pliuskis.feature.devices.DevicesListScreenState.Success
import java.util.concurrent.atomic.AtomicBoolean

private const val STOP_MILLIS = 5_000L
private const val EMPTY_LIST_TIMEOUT_MILLIS = 3_000L

/**
 * Owns device discovery: connects to the broker, then subscribes to the discovery topics. Both run
 * in [viewModelScope], so leaving the screen for longer than [STOP_MILLIS] cancels them. After a
 * failure, [retry] runs the whole attempt again.
 */
class DevicesViewModel(
    private val connect: ConnectUseCase,
    private val observeDevices: ObserveDevicesUseCase,
    private val mapper: (Device) -> DeviceListItem,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    /** Bumped by [retry]; each new value restarts the connect-then-discover attempt. */
    private val attempts = MutableStateFlow(0)

    /** True while a connect call is running, so repeated retries can't queue more blocking connects. */
    private val connecting = AtomicBoolean(false)

    val devices: StateFlow<DevicesListScreenState> = channelFlow {
        attempts.collectLatest {
            connectThenDiscover().collect { state -> send(state) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = WhileSubscribed(STOP_MILLIS),
        initialValue = Loading
    )

    /** Runs connect-then-discover again after a failure. Ignored while a connect is still running. */
    fun retry() {
        if (devices.value is Error && connecting.compareAndSet(false, true)) {
            attempts.update { it + 1 }
        }
    }

    private fun connectThenDiscover(): Flow<DevicesListScreenState> = flow {
        emit(Loading)
        connecting.set(true)
        // The connect call blocks, so it runs on ioDispatcher. Discovery starts only once it succeeds.
        val connection = try {
            connect(Unit).flowOn(ioDispatcher).first()
        } finally {
            connecting.set(false)
        }
        emitAll(
            connection.fold(
                ifLeft = { flowOf(Error(it)) },
                ifRight = { discoverDevices() }
            )
        )
    }

    private fun discoverDevices(): Flow<DevicesListScreenState> =
        observeDevices(Unit)
            .map { result ->
                result.fold(
                    ifRight = { devices -> Success(devices.map(mapper)) },
                    ifLeft = { Error(it) }
                )
            }
            .emptyAfterTimeout()
}

/**
 * Retained discovery messages arrive right after subscribing, but MQTT has no "end of snapshot"
 * marker. When nothing at all arrives within [EMPTY_LIST_TIMEOUT_MILLIS], the broker has no
 * devices, so show the empty list instead of loading forever.
 */
private fun Flow<DevicesListScreenState>.emptyAfterTimeout(): Flow<DevicesListScreenState> =
    channelFlow {
        val emptyFallback = launch {
            delay(EMPTY_LIST_TIMEOUT_MILLIS)
            send(Success(emptyList()))
        }
        collect { state ->
            emptyFallback.cancel()
            send(state)
        }
    }
