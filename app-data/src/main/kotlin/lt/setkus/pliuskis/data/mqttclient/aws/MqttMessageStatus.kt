package lt.setkus.pliuskis.data.aws

import com.amazonaws.mobileconnectors.iot.AWSIotMqttMessageDeliveryCallback.MessageDeliveryStatus
import com.amazonaws.mobileconnectors.iot.AWSIotMqttMessageDeliveryCallback.MessageDeliveryStatus.Fail
import com.amazonaws.mobileconnectors.iot.AWSIotMqttMessageDeliveryCallback.MessageDeliveryStatus.Success

enum class MqttMessageStatus {
    DELIVERED,
    FAILED,
    CONNECTING_TO_BROKER
}

fun MessageDeliveryStatus.toMqttConnectionState(): MqttMessageStatus = when(this) {
    Success -> MqttMessageStatus.DELIVERED
    Fail -> MqttMessageStatus.FAILED
}