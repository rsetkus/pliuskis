package lt.setkus.pliuskis.core.presentation.devices

sealed interface DevicesListScreenState {
    data object Loading : DevicesListScreenState
    data class Error(val error: Throwable) : DevicesListScreenState
    data class Success(val devices: List<DeviceListItem>) : DevicesListScreenState
}
