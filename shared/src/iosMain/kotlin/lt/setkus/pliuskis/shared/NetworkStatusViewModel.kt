package lt.setkus.pliuskis.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import lt.setkus.pliuskis.data.util.NetworkConnectivity

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * Whether the phone has no network, for the offline banner of the device list (Swift: created with
 * `createNetworkStatusViewModel()`). On Android the banner is app-level (`PliuskisAppState.isOffline`); this is the
 * same state for a SwiftUI screen, which owns it like its other ViewModels.
 */
class NetworkStatusViewModel internal constructor(connectivity: NetworkConnectivity) : ViewModel() {

    /** True while the phone is offline. Starts as false (online), like Android, until the monitor reports. */
    val isOffline: StateFlow<Boolean> = connectivity.isOnline
        .map { online -> !online }
        .stateIn(
            scope = viewModelScope,
            started = WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = false,
        )
}
