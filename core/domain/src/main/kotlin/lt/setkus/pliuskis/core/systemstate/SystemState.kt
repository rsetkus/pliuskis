package lt.setkus.pliuskis.core.systemstate

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.InstantComponentSerializer
import kotlin.time.Instant

@Serializable
data class SystemState(
    val humidity: Float,
    val waterLevel: Float,
    // Wire format {"epochSeconds", "nanosecondsOfSecond"}.
    // kotlinx-datetime 0.7 dropped its serializer; kotlinx.serialization now ships it for kotlin.time.Instant.
    @Serializable(with = InstantComponentSerializer::class)
    val timestamp: Instant
)
