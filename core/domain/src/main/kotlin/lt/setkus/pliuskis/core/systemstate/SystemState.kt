package lt.setkus.pliuskis.core.systemstate

import kotlinx.datetime.Instant
import kotlinx.datetime.serializers.InstantComponentSerializer
import kotlinx.serialization.Serializable

@Serializable
data class SystemState(
    val humidity: Float,
    val waterLevel: Float,
    @Serializable(with = InstantComponentSerializer::class)
    val timestamp: Instant
)
