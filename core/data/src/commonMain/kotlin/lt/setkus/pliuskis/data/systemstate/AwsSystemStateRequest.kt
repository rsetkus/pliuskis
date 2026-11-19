package lt.setkus.pliuskis.data.systemstate

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import lt.setkus.pliuskis.core.systemstate.SystemStateRequestable
import lt.setkus.pliuskis.data.IotManager

class AwsSystemStateRequest(
    private val manager: IotManager
) : SystemStateRequestable {
    override suspend fun requestSystemState(deviceId: String) {
        return manager.publishString(
            Json.encodeToString(SystemUpdateMessageModel(manager.getClientId())),
            "devices/$deviceId/status"
        )
    }
}
