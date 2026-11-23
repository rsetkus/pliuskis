package lt.setkus.pliuskis.feature.devices.navigation

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
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
 * phones. [detailPane] draws the detail, so this module doesn't depend on the control feature; [topBarActions] go
 * to the list's top bar, so it doesn't depend on the sign-in feature either.
 */
fun NavGraphBuilder.devicesScreen(
    detailPane: DeviceDetailPane,
    topBarActions: @Composable RowScope.() -> Unit = {},
) {
    composable(route = DEVICES_ROUTE_PATTERN) {
        DevicesListDetailRoute(detailPane = detailPane, topBarActions = topBarActions)
    }
}
