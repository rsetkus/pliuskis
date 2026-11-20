package lt.setkus.pliuskis.core.connect

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import lt.setkus.pliuskis.core.BaseUseCase

/**
 * Emits `Right(Unit)` for every reconnect of the MQTT client reported by [source] (see [Reconnectable.reconnects]).
 * A failure of the source ends the flow with one `Left`.
 */
class ObserveReconnectsUseCase(
    private val source: Reconnectable
) : BaseUseCase<Unit, Unit> {

    override fun invoke(param: Unit): Flow<Either<Throwable, Unit>> =
        source.reconnects()
            .map<Unit, Either<Throwable, Unit>> { it.right() }
            .catch { emit(it.left()) }
}
