package lt.setkus.pliuskis.feature.devices

import androidx.annotation.DrawableRes
import lt.setkus.pliuskis.core.devicelist.DeviceDomainModel
import lt.setkus.pliuskis.core.devicelist.DeviceType

data class DeviceListItem(
    val name: String,
    val id: String,
    @DrawableRes
    val icon: Int,
    val time: String
)

 fun fromDomainModel(domainModel: DeviceDomainModel): DeviceListItem {
    val icon = when (domainModel.type) {
        DeviceType.PLANT -> R.drawable.ic_plant
        else -> R.drawable.ic_unknown
    }

    return DeviceListItem(
        domainModel.name,
        domainModel.deviceId,
        icon,
        domainModel.timestamp.toString()
    )
 }
