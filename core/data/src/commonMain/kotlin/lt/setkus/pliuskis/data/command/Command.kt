package lt.setkus.pliuskis.data.command

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Command(val command: String) {
    @SerialName("water")
    WATER_COMMAND("water"),
}

@Serializable
data class CommandMessage(val command: Command)
