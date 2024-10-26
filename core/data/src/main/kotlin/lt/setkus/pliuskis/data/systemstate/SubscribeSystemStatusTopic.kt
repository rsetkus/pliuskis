package lt.setkus.pliuskis.data.systemstate

import lt.setkus.pliuskis.core.systemstate.SystemStateSubscribable
import lt.setkus.pliuskis.data.IotManager

private const val SYSTEM_STATUS_TOPIC = "application/%s/%s/status"

class SubscribeSystemStatusTopic(
    private val manager: IotManager
) : SystemStateSubscribable {
    override fun subscribeState(deviceId: String) =
        manager.subscribe(
            SYSTEM_STATUS_TOPIC.format(manager.getClientId(), deviceId)
        )
}