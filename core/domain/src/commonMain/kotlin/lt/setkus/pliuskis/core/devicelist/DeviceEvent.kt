package lt.setkus.pliuskis.core.devicelist

/**
 * Raw discovery message received for a device. [deviceId] comes from the topic, so it is
 * known even when the payload is empty or malformed.
 */
sealed interface DeviceEvent {
    val deviceId: String

    /** JSON description of the device; an empty payload means the device was removed. */
    data class Info(override val deviceId: String, val payload: String) : DeviceEvent

    /** `online` or `offline`; `offline` is also published by the broker as the device's Last Will. */
    data class Availability(override val deviceId: String, val payload: String) : DeviceEvent
}
