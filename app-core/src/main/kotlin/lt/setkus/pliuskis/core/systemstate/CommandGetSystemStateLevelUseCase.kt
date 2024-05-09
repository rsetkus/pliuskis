package lt.setkus.pliuskis.core.systemstate

import kotlinx.coroutines.flow.flow
import lt.setkus.pliuskis.core.BaseUseCase

class CommandGetSystemStateLevelUseCase(private val repository: SystemStateRequestable) :
    BaseUseCase<Unit, String> {
    override fun invoke(param: Unit) = repository.requestSystemState()
}