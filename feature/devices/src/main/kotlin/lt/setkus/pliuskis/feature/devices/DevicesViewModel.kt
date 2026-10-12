package lt.setkus.pliuskis.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lt.setkus.pliuskis.core.devicelist.Device
import lt.setkus.pliuskis.core.devicelist.ObserveDevicesUseCase
import lt.setkus.pliuskis.feature.devices.DevicesListScreenState.Error
import lt.setkus.pliuskis.feature.devices.DevicesListScreenState.Loading
import lt.setkus.pliuskis.feature.devices.DevicesListScreenState.Success

private const val STOP_MILLIS = 5_000L
private const val EMPTY_LIST_TIMEOUT_MILLIS = 3_000L

class DevicesViewModel(
    observeDevices: ObserveDevicesUseCase,
    mapper: (Device) -> DeviceListItem
) : ViewModel() {

    val devices: StateFlow<DevicesListScreenState> = observeDevices(Unit)
        .map { result ->
            result.fold(
                ifRight = { devices -> Success(devices.map(mapper)) },
                ifLeft = { Error(it) }
            )
        }
        .emptyAfterTimeout()
        .stateIn(
            scope = viewModelScope,
            started = WhileSubscribed(STOP_MILLIS),
            initialValue = Loading
        )
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
