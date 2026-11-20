package lt.setkus.pliuskis.core.presentation.control

import kotlin.time.Instant

/** How the plant feels about its soil humidity. */
enum class HumidityStatus { THIRSTY, GOOD }

/** Whether the device's water tank needs a refill. */
enum class WaterLevelStatus { LOW, ENOUGH }

/**
 * The meaning of one status reading of a device, without display text. Each platform turns it into its own text and
 * date format.
 *
 * [soilMoisturePercent] and [waterLevelPercent] are the raw readings as whole percentages (0..100); the firmware
 * reports both on a 0..10 scale, 10 being wet soil or a full tank.
 */
data class DeviceStatus(
    val humidity: HumidityStatus,
    val waterLevel: WaterLevelStatus,
    val updatedAt: Instant,
    val soilMoisturePercent: Int,
    val waterLevelPercent: Int,
)
