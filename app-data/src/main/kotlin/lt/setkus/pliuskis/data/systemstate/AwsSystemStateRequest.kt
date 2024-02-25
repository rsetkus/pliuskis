package lt.setkus.pliuskis.data.systemstate

import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import lt.setkus.pliuskis.core.systemstate.SystemStateRequestable

class AwsSystemStateRequest(private val manager: AWSIotMqttManager) : SystemStateRequestable {
    override fun requestSystemState() {
        TODO("Not implemented")
    }
}