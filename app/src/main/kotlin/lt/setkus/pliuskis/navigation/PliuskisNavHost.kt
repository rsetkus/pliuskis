package lt.setkus.pliuskis.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import lt.setkus.pliuskis.feature.devices.navigation.DEVICES_ROUTE
import lt.setkus.pliuskis.feature.devices.navigation.forDevicesScreen
import lt.setkus.pliuskis.ui.PliuskisAppState

@Composable
fun PliuskisNavHost(
    appState: PliuskisAppState,
    onShowSnackbar: suspend (String, String?) -> Boolean,
    modifier: Modifier = Modifier,
    startDestination: String = DEVICES_ROUTE,
) {
    val navController = appState.navController
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        forDevicesScreen({  })
    }
}