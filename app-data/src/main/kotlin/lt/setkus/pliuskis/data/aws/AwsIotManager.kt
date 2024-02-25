package lt.setkus.pliuskis.data.aws

import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import kotlinx.coroutines.flow.Flow
import lt.setkus.pliuskis.data.IotManager

class AwsIotManager(private val awsIotManager: AWSIotMqttManager) : IotManager {
    override fun connect(): Flow<MqttConnectionState> {
        TODO("Not yet implemented")
    }
}