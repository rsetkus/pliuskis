package lt.setkus.pliuskis.feature.devices.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import lt.setkus.pliuskis.feature.devices.DeviceDetailPane
import lt.setkus.pliuskis.feature.devices.DevicesListDetailRoute

const val DEVICES_ROUTE_PATTERN = "devices_route"

fun NavController.navigateDevices(options: NavOptions) = navigate(DEVICES_ROUTE_PATTERN, options)

/**
 * The garden: the device list, and the selected device's detail beside it on wide windows or on top of it on
 * phones. [detailPane] draws the detail, so this module doesn't depend on the control feature.
 */
fun NavGraphBuilder.devicesScreen(detailPane: DeviceDetailPane) {
    composable(route = DEVICES_ROUTE_PATTERN) {
        DevicesListDetailRoute(detailPane = detailPane)
    }
}
