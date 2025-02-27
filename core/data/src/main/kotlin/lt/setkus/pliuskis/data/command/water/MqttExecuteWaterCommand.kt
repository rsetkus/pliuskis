package lt.setkus.pliuskis.data.command.water

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import lt.setkus.pliuskis.core.command.water.ExecuteWaterCommand
import lt.setkus.pliuskis.data.IotManager
import lt.setkus.pliuskis.data.command.Command
import lt.setkus.pliuskis.data.command.CommandMessage

class MqttExecuteWaterCommand(
    private val iotManager: IotManager
) : ExecuteWaterCommand {

    /**
     * Executes the command to water plant
     * @param i - device id
     */
    override fun execute(i: String) {
        val message = Json.encodeToString(CommandMessage(Command.WATER_COMMAND))
        iotManager.publishString(
            message = message,
            topic = "devices/$i/command"
        )
    }
}