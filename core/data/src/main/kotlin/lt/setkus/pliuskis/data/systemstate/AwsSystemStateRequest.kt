package lt.setkus.pliuskis.data.systemstate

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import lt.setkus.pliuskis.core.systemstate.SystemStateRequestable
import lt.setkus.pliuskis.data.IotManager

class AwsSystemStateRequest(
    private val manager: IotManager
) : SystemStateRequestable {
    override fun requestSystemState(deviceId: String) {
        return manager.publishString(
            Json.encodeToString(SystemUpdateMessageModel(manager.getClientId())),
            "devices/$deviceId/status"
        )
    }
}