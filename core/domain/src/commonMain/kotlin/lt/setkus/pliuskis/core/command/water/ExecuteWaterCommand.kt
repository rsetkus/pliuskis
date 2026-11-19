package lt.setkus.pliuskis.core.command.water

import lt.setkus.pliuskis.core.command.CommandExecutable

interface ExecuteWaterCommand : CommandExecutable<String> {
    override suspend fun execute(i: String)
}
