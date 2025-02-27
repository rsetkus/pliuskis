package lt.setkus.pliuskis.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import lt.setkus.feature.control.navigation.controlScreen
import lt.setkus.feature.control.navigation.navigateControl
import lt.setkus.pliuskis.feature.devices.navigation.DEVICES_ROUTE_PATTERN
import lt.setkus.pliuskis.feature.devices.navigation.devicesScreen
import lt.setkus.pliuskis.ui.PliuskisAppState

@Composable
fun PliuskisNavHost(
    appState: PliuskisAppState,
    onShowSnackbar: suspend (String, String?) -> Boolean,
    modifier: Modifier = Modifier,
    startDestination: String = DEVICES_ROUTE_PATTERN,
) {
    val navController = appState.navController
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        devicesScreen(
            onDeviceClick = navController::navigateControl,
            nestedGraphs = {
                controlScreen(
                    onBackClick = navController::popBackStack,
                )
            }
        )
    }
}