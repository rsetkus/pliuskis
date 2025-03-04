package lt.setkus.pliuskis.core.systemstate

import arrow.core.Either
import kotlinx.coroutines.flow.flow
import lt.setkus.pliuskis.core.BaseUseCase

class RequestSystemStateUseCase(
    private val repository: SystemStateRequestable
) : BaseUseCase<String, Unit> {
    override fun invoke(param: String) =
        flow {
            emit(tryRequestSystemState(param))
        }

    private fun tryRequestSystemState(param: String): Either<Throwable, Unit> =
        Either.catch {
            repository.requestSystemState(param)
        }.mapLeft {
            it
        }
}
