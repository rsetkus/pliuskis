package lt.setkus.pliuskis.core.testing.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import lt.setkus.pliuskis.data.util.NetworkConnectivity

class TestNetworkConnectivity : NetworkConnectivity {
    private val connectivityFlow = MutableStateFlow(true)

    override val isOnline: Flow<Boolean> = connectivityFlow

    /**
     * A test-only API to set the connectivity state from tests.
     */
    fun setConnected(isConnected: Boolean) {
        connectivityFlow.value = isConnected
    }
}
