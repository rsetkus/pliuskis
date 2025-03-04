package lt.setkus.pliuskis.core.devicelist

import kotlinx.coroutines.flow.Flow

interface DeviceSubscribable {
    fun subcsribeDevice(): Flow<String>
}
