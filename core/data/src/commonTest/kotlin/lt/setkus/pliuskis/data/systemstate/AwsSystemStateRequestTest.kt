package lt.setkus.pliuskis.data.systemstate

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import lt.setkus.pliuskis.data.wire.PublishedMessage
import lt.setkus.pliuskis.data.wire.RecordingIotManager

class AwsSystemStateRequestTest : FunSpec({

    val iotManager = RecordingIotManager("client-id")

    val awsSystemStateRequest = AwsSystemStateRequest(iotManager)

    test(
        """
        GIVEN aws manager
        WHEN published message to aws manager for system update
        THEN should complete without error
    """
    ) {
        awsSystemStateRequest.requestSystemState("1234")
        iotManager.published shouldContain
            PublishedMessage("devices/1234/status", """{"applicationId":"client-id"}""")
    }
})
