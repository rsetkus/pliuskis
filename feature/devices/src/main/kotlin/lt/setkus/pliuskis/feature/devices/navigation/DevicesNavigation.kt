package lt.setkus.pliuskis.feature.devices.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import lt.setkus.pliuskis.feature.devices.DevicesListRoute

const val DEVICES_ROUTE = "devices"

fun NavController.navigateDevices(options: NavOptions) = navigate(DEVICES_ROUTE, options)

fun NavGraphBuilder.forDevicesScreen(onDeviceClick: (String) -> Unit)  {
    composable(
        route = DEVICES_ROUTE
    ) {
        DevicesListRoute(onDeviceClick = onDeviceClick)
    }
}