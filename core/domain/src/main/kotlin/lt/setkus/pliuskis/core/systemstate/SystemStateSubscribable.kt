package lt.setkus.pliuskis.core.systemstate

import kotlinx.coroutines.flow.Flow

interface SystemStateSubscribable {
    fun subscribeState(deviceId: String): Flow<String>
}
