package lt.setkus.pliuskis.data.systemstate

import kotlinx.coroutines.flow.map
import lt.setkus.pliuskis.core.systemstate.SystemStateSubscribable
import lt.setkus.pliuskis.data.IotManager

/** The topic the device answers a status request on: `application/{applicationId}/{deviceId}/status`. */
private fun systemStatusTopic(applicationId: String, deviceId: String): String =
    "application/$applicationId/$deviceId/status"

class SubscribeSystemStatusTopic(
    private val manager: IotManager
) : SystemStateSubscribable {
    override fun subscribeState(deviceId: String) =
        manager
            .subscribe(systemStatusTopic(manager.getClientId(), deviceId))
            .map { it.payload }
}
