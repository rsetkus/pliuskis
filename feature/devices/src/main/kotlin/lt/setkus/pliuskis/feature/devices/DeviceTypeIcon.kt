package lt.setkus.pliuskis.feature.devices

import androidx.annotation.DrawableRes
import lt.setkus.pliuskis.core.devicelist.DeviceType

/** The drawable the device list shows for a device of this type. */
@DrawableRes
internal fun DeviceType.iconRes(): Int = when (this) {
    DeviceType.PLANT -> R.drawable.ic_plant
    else -> R.drawable.ic_unknown
}
