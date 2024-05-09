package lt.setkus.pliuskis.data.systemstate

import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos
import io.kotest.core.spec.style.FunSpec
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import lt.setkus.pliuskis.data.IotManager

class AwsSystemStateRequestTest : FunSpec({

    val mockIotManager = mockk<IotManager>(relaxed = true)

    val awsSystemStateRequest = AwsSystemStateRequest(mockIotManager)

    test("""
        GIVEN aws manager
        WHEN published message to aws manager for system update
        THEN should complete without error
    """) {
        awsSystemStateRequest.requestSystemState()
        coVerify { mockIotManager.publishString("state", "system/*/state") }
    }
})