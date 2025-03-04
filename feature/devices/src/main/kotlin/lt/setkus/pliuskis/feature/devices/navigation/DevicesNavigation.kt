package lt.setkus.pliuskis.feature.devices.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import lt.setkus.pliuskis.feature.devices.DevicesListRoute

const val DEVICES_ROUTE_PATTERN = "devices_route"
const val START_DEVICES_ROUTE = "start_devices_route"

fun NavController.navigateDevices(options: NavOptions) = navigate(DEVICES_ROUTE_PATTERN, options)

fun NavGraphBuilder.devicesScreen(
    onDeviceClick: (String) -> Unit,
    nestedGraphs: NavGraphBuilder.() -> Unit
) {
    navigation(
        route = DEVICES_ROUTE_PATTERN,
        startDestination = START_DEVICES_ROUTE
    ) {
        composable(route = START_DEVICES_ROUTE) {
            DevicesListRoute(onDeviceClick = onDeviceClick)
        }
        nestedGraphs()
    }
}
