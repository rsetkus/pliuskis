package lt.setkus.pliuskis.data.aws

import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connected
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connecting
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.ConnectionLost
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Reconnecting
import lt.setkus.pliuskis.data.aws.MqttConnectionState.*


fun AWSIotMqttClientStatus.toConnectionState() =
    when (this) {
        Connecting -> CONNECTING
        Connected -> CONNECTED
        ConnectionLost -> FAILED
        Reconnecting -> CONNECTING
    }