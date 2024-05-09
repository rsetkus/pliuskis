package lt.setkus.pliuskis.data.aws

import app.cash.turbine.test
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connected
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connecting
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import com.amazonaws.mobileconnectors.iot.AWSIotMqttMessageDeliveryCallback
import com.amazonaws.mobileconnectors.iot.AWSIotMqttMessageDeliveryCallback.MessageDeliveryStatus.Success
import io.kotest.common.runBlocking
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import lt.setkus.pliuskis.data.aws.MqttConnectionState.CONNECTED
import lt.setkus.pliuskis.data.aws.MqttConnectionState.CONNECTING
import lt.setkus.pliuskis.data.aws.MqttMessageStatus.*
import lt.setkus.pliuskis.data.crypto.KeyStoreProducer
import java.security.KeyStore

class AwsIotManagerTest : FunSpec({
    val mockAWSIotMqttManager = mockk<AWSIotMqttManager>(relaxed = true)
    val mockKeyStoreProducer = mockk<KeyStoreProducer>(relaxed = true)

    val awsManager = AwsIotManager(mockAWSIotMqttManager, mockKeyStoreProducer)

    test("""
        GIVEN aws manager
        WHEN connected and published string has been delivered
        THEN should return delivered status
    """) {
        every { mockAWSIotMqttManager.connect(any<KeyStore>(), any()) } answers {
            secondArg<AWSIotMqttClientStatusCallback>().onStatusChanged(Connected, null)
        }
        every { mockAWSIotMqttManager.publishString(any(), any(), any(), any(), any()) } answers {
            arg<AWSIotMqttMessageDeliveryCallback>(3).statusChanged(Success, null)
        }

        awsManager.publishString("", "").test {
            awaitItem() shouldBe DELIVERED
        }

        verify { mockKeyStoreProducer.getKeyStore() }
    }
})