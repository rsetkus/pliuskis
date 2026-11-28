package lt.setkus.pliuskis.navigation

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import lt.setkus.feature.control.ControlPane
import lt.setkus.pliuskis.feature.devices.navigation.DEVICES_ROUTE_PATTERN
import lt.setkus.pliuskis.feature.devices.navigation.devicesScreen
import lt.setkus.pliuskis.feature.signin.navigation.signInScreen
import lt.setkus.pliuskis.ui.PliuskisAppState

/**
 * The app's destinations: sign-in and the devices (list and control). [startDestination] is the first one for the
 * session at launch; after that `PliuskisAppState.showScreenFor` switches between them. [deviceListActions] go to the
 * device list's top bar (the account menu).
 */
@Composable
fun PliuskisNavHost(
    appState: PliuskisAppState,
    onShowSnackbar: suspend (String, String?) -> Boolean,
    modifier: Modifier = Modifier,
    startDestination: String = DEVICES_ROUTE_PATTERN,
    deviceListActions: @Composable RowScope.() -> Unit = {},
) {
    val navController = appState.navController
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        signInScreen()
        devicesScreen(
            detailPane = { deviceId, deviceName, showBack, onBack ->
                ControlPane(
                    deviceId = deviceId,
                    deviceName = deviceName,
                    showBack = showBack,
                    onBackClick = onBack
                )
            },
            topBarActions = deviceListActions
        )
    }
}
