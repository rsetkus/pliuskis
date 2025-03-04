package lt.setkus.pliuskis.core.systemstate

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import lt.setkus.pliuskis.core.BaseUseCase

class GetSystemStatusUseUseCase(
    private val subsribable: SystemStateSubscribable
) : BaseUseCase<String, SystemState> {
    override fun invoke(deviceId: String) = flow<Either<Throwable, SystemState>> {
        subsribable.subscribeState(deviceId).collect {
                emit(decode(it).right())
            }
        }.catch {
            emit(it.left())
        }

    private fun decode(content: String) = Json.decodeFromString(SystemState.serializer(), content)
}
