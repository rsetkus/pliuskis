package lt.setkus.pliuskis.feature.devices

sealed interface DevicesListScreenState {
    data object Loading : DevicesListScreenState
    data class Error(val error: Throwable) : DevicesListScreenState
    data class Success(val device: String) : DevicesListScreenState
}