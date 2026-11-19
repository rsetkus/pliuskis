package lt.setkus.feature.control

/** The control screen's display text for a [lt.setkus.pliuskis.core.presentation.control.DeviceStatus]. */
data class DeviceStatusText(
    val humidity: String,
    val waterLevel: String,
    val updatedTime: String
)
