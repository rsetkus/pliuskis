package lt.setkus.pliuskis.data.systemstate

import io.kotest.core.spec.style.FunSpec
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import lt.setkus.pliuskis.data.IotManager

class AwsSystemStateRequestTest : FunSpec({

    val mockIotManager = mockk<IotManager>(relaxed = true) {
        every { getClientId() } returns "client-id"
    }

    val awsSystemStateRequest = AwsSystemStateRequest(mockIotManager)

    test("""
        GIVEN aws manager
        WHEN published message to aws manager for system update
        THEN should complete without error
    """) {
        awsSystemStateRequest.requestSystemState("1234")
        coVerify {
            mockIotManager.publishString("""{"applicationId":"client-id"}""", "devices/1234/status")
        }
    }
})
