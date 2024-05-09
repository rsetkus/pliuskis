package lt.setkus.pliuskis.data.systemstate

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import lt.setkus.pliuskis.core.systemstate.SystemStateRequestable
import lt.setkus.pliuskis.data.IotManager

/**
 *
 */
class AwsSystemStateRequest(
    private val manager: IotManager
) : SystemStateRequestable {
    override fun requestSystemState(): Flow<String> {
        return manager.publishString("state", "system/*/state").map { it.toString() }
    }
}