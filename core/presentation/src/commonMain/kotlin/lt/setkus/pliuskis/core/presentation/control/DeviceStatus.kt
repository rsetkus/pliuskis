package lt.setkus.pliuskis.core.presentation.control

import kotlin.time.Instant

/** How the plant feels about its soil humidity. */
enum class HumidityStatus { THIRSTY, GOOD }

/** Whether the device's water tank needs a refill. */
enum class WaterLevelStatus { LOW, ENOUGH }

/**
 * The meaning of one status reading of a device, without display text. Each platform turns it into its own text and
 * date format.
 */
data class DeviceStatus(
    val humidity: HumidityStatus,
    val waterLevel: WaterLevelStatus,
    val updatedAt: Instant,
)
