package lt.setkus.pliuskis.core.devicelist

import kotlinx.serialization.Serializable

@Serializable
data class DeviceDomainModel(
    val name: String,
    val deviceId: String,
    val timestamp: String
)
