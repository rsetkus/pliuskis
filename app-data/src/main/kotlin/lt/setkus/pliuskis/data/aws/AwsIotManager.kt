package lt.setkus.pliuskis.data.aws

import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connected
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import com.amazonaws.mobileconnectors.iot.AWSIotMqttMessageDeliveryCallback
import com.amazonaws.mobileconnectors.iot.AWSIotMqttMessageDeliveryCallback.MessageDeliveryStatus
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos.QOS0
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import lt.setkus.pliuskis.data.IotManager
import lt.setkus.pliuskis.data.aws.MqttConnectionState.CONNECTED
import lt.setkus.pliuskis.data.aws.MqttConnectionState.CONNECTING
import lt.setkus.pliuskis.data.aws.MqttConnectionState.FAILED
import lt.setkus.pliuskis.data.aws.MqttMessageStatus.CONNECTING_TO_BROKER
import lt.setkus.pliuskis.data.crypto.KeyStoreProducer
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AwsIotManager(
    private val awsIotManager: AWSIotMqttManager, private val keyStoreProducer: KeyStoreProducer
) : IotManager {

    override fun connect() = callbackFlow {
        awsIotManager.connect(keyStoreProducer.getKeyStore()) { status: AWSIotMqttClientStatus, _: Throwable? ->
            Timber.d("Connection status: $status")
            trySendBlocking(status.toConnectionState())
        }

        awaitClose {
            awsIotManager.disconnect()
        }
    }

    override fun publishString(message: String, topic: String) = connect()
        .map {
            when (it) {
                CONNECTING -> CONNECTING_TO_BROKER
                CONNECTED -> sendString(message, topic)
                FAILED -> TODO()
            }
        }

    private suspend fun sendString(message: String, topic: String): MqttMessageStatus =
        suspendCoroutine { continuation ->
            val callback =
                AWSIotMqttMessageDeliveryCallback { status: MessageDeliveryStatus, _: Any? ->
                    continuation.resume(status.toMqttConnectionState())
                }
            awsIotManager.publishString(message, topic, QOS0, callback, "")
        }
}