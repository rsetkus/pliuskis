package lt.setkus.pliuskis.core.devicelist

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.serializers.InstantComponentSerializer
import kotlinx.serialization.Serializable

@Serializable
data class DeviceDomainModel(
    val name: String,
    val deviceId: String,
    @Serializable(with = InstantComponentSerializer::class)
    val timestamp: Instant
)
