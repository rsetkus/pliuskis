package lt.setkus.pliuskis.data.util

import kotlinx.coroutines.flow.Flow

interface NetworkConnectivity {
    val isOnline: Flow<Boolean>
}