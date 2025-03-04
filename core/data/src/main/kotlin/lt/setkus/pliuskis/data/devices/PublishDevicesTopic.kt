package lt.setkus.pliuskis.data.devices

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import lt.setkus.pliuskis.core.devicelist.DeviceListPublishable
import lt.setkus.pliuskis.data.IotManager

class PublishDevicesTopic(
    private val manager: IotManager
) : DeviceListPublishable {
    override fun publishToRespond() {
        val message = Json.encodeToString(PublishDeviceListTopicMessage(manager.getClientId()))
        manager.publishString(message, "devices/*/list")
    }
}
