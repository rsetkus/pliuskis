package lt.setkus.pliuskis.core.devicelist

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import lt.setkus.pliuskis.core.BaseUseCase

class GetDeviceUseCase(
    private val subrcibable: DeviceSubscribable
) : BaseUseCase<Unit, DeviceDomainModel> {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun invoke(deviceId: Unit) = flow<Either<Throwable, DeviceDomainModel>> {
        subrcibable.subcsribeDevice().collect {
            emit(decodeDevice(it).right())
        }
    }.catch { emit(it.left()) }

    private fun decodeDevice(content: String): DeviceDomainModel =
        json.decodeFromString(
            DeviceDomainModel.serializer(),
            content
        )
}
