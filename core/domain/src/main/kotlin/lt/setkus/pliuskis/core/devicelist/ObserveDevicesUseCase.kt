package lt.setkus.pliuskis.core.devicelist

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.serialization.json.Json
import lt.setkus.pliuskis.core.BaseUseCase
import timber.log.Timber

private const val ONLINE = "online"

/**
 * Folds the retained discovery messages into the current list of devices, sorted by name.
 * Emits a new list after every message. A malformed message is logged and skipped, so one
 * misbehaving device cannot break discovery for the others.
 */
class ObserveDevicesUseCase(
    private val subscribable: DeviceSubscribable
) : BaseUseCase<Unit, List<Device>> {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun invoke(param: Unit): Flow<Either<Throwable, List<Device>>> =
        subscribable.subscribeDevices()
            .runningFold(DeviceRegistry(), ::reduce)
            .drop(1)
            .map<DeviceRegistry, Either<Throwable, List<Device>>> { it.devices().right() }
            .catch { emit(it.left()) }

    private fun reduce(registry: DeviceRegistry, event: DeviceEvent): DeviceRegistry =
        when (event) {
            is DeviceEvent.Info -> reduceInfo(registry, event)
            is DeviceEvent.Availability -> registry.copy(
                online = registry.online + (event.deviceId to (event.payload == ONLINE))
            )
        }

    private fun reduceInfo(registry: DeviceRegistry, event: DeviceEvent.Info): DeviceRegistry =
        if (event.payload.isEmpty()) {
            registry.copy(infos = registry.infos - event.deviceId)
        } else {
            decode(event.payload).fold(
                ifLeft = { error ->
                    Timber.w(error, "Skipping malformed info from device ${event.deviceId}")
                    registry
                },
                ifRight = { info ->
                    // The topic is authoritative: it is what command and status topics are built from.
                    val device = info.copy(deviceId = event.deviceId)
                    registry.copy(infos = registry.infos + (event.deviceId to device))
                }
            )
        }

    private fun decode(payload: String): Either<Throwable, DeviceDomainModel> =
        Either.catch { json.decodeFromString(DeviceDomainModel.serializer(), payload) }
}

private data class DeviceRegistry(
    val infos: Map<String, DeviceDomainModel> = emptyMap(),
    val online: Map<String, Boolean> = emptyMap()
) {
    fun devices(): List<Device> =
        infos
            .map { (deviceId, info) -> Device(info, online[deviceId] ?: false) }
            .sortedBy { it.info.name }
}
