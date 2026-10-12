package lt.setkus.pliuskis.data.devices

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import lt.setkus.pliuskis.core.devicelist.DeviceEvent
import lt.setkus.pliuskis.core.devicelist.DeviceSubscribable
import lt.setkus.pliuskis.data.IotManager
import timber.log.Timber

internal const val INFO_TOPIC_FILTER = "devices/+/info"
internal const val AVAILABILITY_TOPIC_FILTER = "devices/+/availability"

private const val TOPIC_SEGMENT_COUNT = 3
private const val DEVICE_ID_SEGMENT = 1

/**
 * Observes the retained discovery topics every device publishes on connect:
 * `devices/{deviceId}/info` (JSON description) and `devices/{deviceId}/availability`
 * (`online`/`offline`, the latter also set by the broker as the device's Last Will).
 */
class SubscribeDeviceTopics(
    private val manager: IotManager
) : DeviceSubscribable {

    override fun subscribeDevices(): Flow<DeviceEvent> = merge(
        events(INFO_TOPIC_FILTER, DeviceEvent::Info),
        events(AVAILABILITY_TOPIC_FILTER, DeviceEvent::Availability)
    )

    private fun events(
        topicFilter: String,
        toEvent: (deviceId: String, payload: String) -> DeviceEvent
    ): Flow<DeviceEvent> =
        manager.subscribe(topicFilter).mapNotNull { message ->
            deviceIdOf(message.topic)?.let { deviceId -> toEvent(deviceId, message.payload) }
        }

    private fun deviceIdOf(topic: String): String? {
        val segments = topic.split("/")
        val deviceId = segments
            .getOrNull(DEVICE_ID_SEGMENT)
            ?.takeIf { segments.size == TOPIC_SEGMENT_COUNT && it.isNotEmpty() }
        if (deviceId == null) {
            Timber.w("Ignoring message on malformed device topic: $topic")
        }
        return deviceId
    }
}
