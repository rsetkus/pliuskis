package lt.setkus.pliuskis.data.systemstate

import kotlinx.serialization.Serializable

@Serializable
data class SystemUpdateMessageModel(val applicationId: String)
