package lt.setkus.pliuskis.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lt.setkus.pliuskis.core.devicelist.DeviceDomainModel
import lt.setkus.pliuskis.core.devicelist.GetDeviceUseCase
import lt.setkus.pliuskis.core.devicelist.RequestDeviceListUseCase
import lt.setkus.pliuskis.feature.devices.DevicesListScreenState.Error
import lt.setkus.pliuskis.feature.devices.DevicesListScreenState.Loading
import lt.setkus.pliuskis.feature.devices.DevicesListScreenState.Success
import timber.log.Timber

private const val STOP_MILLIS = 5_000L

class DevicesViewModel(
    useCase: RequestDeviceListUseCase,
    devicesUse: GetDeviceUseCase,
    mapper: (DeviceDomainModel) -> DeviceListItem
) : ViewModel() {

    init {
        viewModelScope.launch {
            useCase(Unit)
                .collect { result ->
                    result.fold(
                        ifRight = { Timber.d("Devices list topic published") },
                        ifLeft = { Timber.e("Failed to publish devices list topic: $it") }
                    )
                }
        }
    }

    val devices: StateFlow<DevicesListScreenState> = devicesUse(Unit)
        .map { result ->
            Timber.d(mapper.toString())
            result.fold(
                ifRight = { Success(mapper(it)) },
                ifLeft = { Error(it) }
            )
        }.stateIn(
            scope = viewModelScope,
            started = WhileSubscribed(STOP_MILLIS),
            initialValue = Loading
        )
}
