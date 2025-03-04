package lt.setkus.pliuskis.data.devices

import kotlinx.coroutines.flow.Flow
import lt.setkus.pliuskis.core.devicelist.DeviceSubscribable
import lt.setkus.pliuskis.data.IotManager

class SubcribeDeviceTopic(
    private val manager: IotManager
) : DeviceSubscribable {
    override fun subcsribeDevice(): Flow<String> {
        val applicationId = manager.getClientId()
        return manager.subscribe("application/$applicationId/devices")
    }
}
