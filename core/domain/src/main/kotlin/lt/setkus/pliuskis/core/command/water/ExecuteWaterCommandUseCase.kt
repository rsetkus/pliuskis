package lt.setkus.pliuskis.core.command.water;

import arrow.core.Either
import kotlinx.coroutines.flow.flow
import lt.setkus.pliuskis.core.BaseUseCase
import lt.setkus.pliuskis.core.command.CommandExecutable

class ExecuteWaterCommandUseCase(
    private val commandExecutable: CommandExecutable<String>
) : BaseUseCase<String, Unit> {

    override fun invoke(param: String) = flow {
        emit(execute(param))
    }

    private fun execute(param: String) = Either.catch {
        commandExecutable.execute(param)
    }.mapLeft {
        it
    }
}
