package lt.setkus.pliuskis.core.systemstate

import kotlinx.coroutines.flow.Flow

interface SystemStateRequestable {
    fun requestSystemState(): Flow<String>
}