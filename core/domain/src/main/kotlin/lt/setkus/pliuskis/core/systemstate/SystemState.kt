package lt.setkus.pliuskis.core.systemstate

import kotlinx.serialization.Serializable

@Serializable
data class  SystemState(
    val humidity: Float,
    val waterLevel: Float
)