package lt.setkus.pliuskis.feature.devices

import androidx.annotation.DrawableRes
import lt.setkus.pliuskis.core.devicelist.Device
import lt.setkus.pliuskis.core.devicelist.DeviceType

data class DeviceListItem(
    val name: String,
    val id: String,
    @DrawableRes
    val icon: Int,
    val online: Boolean
)

fun fromDomainModel(device: Device): DeviceListItem {
    val icon = when (device.info.type) {
        DeviceType.PLANT -> R.drawable.ic_plant
        else -> R.drawable.ic_unknown
    }

    return DeviceListItem(
        device.info.name,
        device.info.deviceId,
        icon,
        device.online
    )
}
