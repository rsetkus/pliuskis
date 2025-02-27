package lt.setkus.pliuskis.core.connect

import arrow.core.Either
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import lt.setkus.pliuskis.core.BaseUseCase

class ConnectUseCase(
    private val connectSource: Connectable
): BaseUseCase<Unit, String>  {

    override fun invoke(param: Unit): Flow<Either<Throwable, String>> =
        flow { emit(tryConnect()) }

    private fun tryConnect(): Either<Throwable, String> =
        Either.catch {
            connectSource.connect()
        }.mapLeft { it }
}