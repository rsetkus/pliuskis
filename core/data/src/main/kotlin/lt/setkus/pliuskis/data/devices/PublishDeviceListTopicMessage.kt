package lt.setkus.pliuskis.data.devices

import kotlinx.serialization.Serializable

@Serializable
data class PublishDeviceListTopicMessage(
    val applicationId: String
)
