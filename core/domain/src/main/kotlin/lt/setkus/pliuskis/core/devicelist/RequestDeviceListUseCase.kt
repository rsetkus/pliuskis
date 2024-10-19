package lt.setkus.pliuskis.core.devicelist

import arrow.core.Either
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import lt.setkus.pliuskis.core.BaseUseCase

class RequestDeviceListUseCase(
    private val deviceListRepository: DeviceListPublishable
) : BaseUseCase<Unit, Unit> {
    override fun invoke(param: Unit): Flow<Either<Throwable, Unit>> =
        flow { emit(tryPublish()) }

    private fun tryPublish(): Either<Throwable, Unit> =
        Either.catch {
            deviceListRepository.publishToRespond()
        }.mapLeft { it }
}