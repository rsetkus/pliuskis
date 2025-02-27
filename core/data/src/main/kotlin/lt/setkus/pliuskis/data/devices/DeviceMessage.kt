package lt.setkus.pliuskis.data.devices

import kotlinx.serialization.Serializable

@Serializable
data class DeviceMessage(
    val name: String,
    val deviceId: String,
    val timestamp: String
)
