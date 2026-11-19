package lt.setkus.pliuskis.core.presentation.devices

import lt.setkus.pliuskis.core.devicelist.Device
import lt.setkus.pliuskis.core.devicelist.DeviceType
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/** One device in the device list. Each platform picks the icon for [type]. */
data class DeviceListItem(
    val name: String,
    val id: String,
    val type: DeviceType,
    val online: Boolean
)

/** Maps a domain [Device] to its list item. Android and the `shared` module use it; Swift gets the items only. */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
fun fromDomainModel(device: Device): DeviceListItem =
    DeviceListItem(
        device.info.name,
        device.info.deviceId,
        device.info.type,
        device.online
    )
