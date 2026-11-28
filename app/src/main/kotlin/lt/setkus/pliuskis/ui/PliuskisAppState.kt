package lt.setkus.pliuskis.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import lt.setkus.pliuskis.core.presentation.session.SessionState
import lt.setkus.pliuskis.data.util.NetworkConnectivity
import lt.setkus.pliuskis.feature.devices.navigation.DEVICES_ROUTE_PATTERN
import lt.setkus.pliuskis.feature.signin.navigation.SIGN_IN_ROUTE

private const val STOP_TIMEOUT_MILLIS = 5_000L

@Composable
fun rememberPliuskisAppState(
    connectivity: NetworkConnectivity,
    navHostController: NavHostController = rememberNavController(),
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): PliuskisAppState {
    return remember(navHostController, coroutineScope) {
        PliuskisAppState(navHostController, coroutineScope, connectivity)
    }
}

@Stable
class PliuskisAppState(
    val navController: NavHostController,
    coroutineScope: CoroutineScope,
    connectivity: NetworkConnectivity
) {
    val currentDestination: NavDestination?
        @Composable get() = navController.currentBackStackEntryAsState().value?.destination

    val isOffline = connectivity.isOnline
        .map(Boolean::not)
        .stateIn(
            scope = coroutineScope,
            started = WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = false
        )

    /** The destination the app shows for [session]: the devices when signed in, sign-in otherwise. */
    fun destinationFor(session: SessionState): String =
        if (session == SessionState.SignedIn) DEVICES_ROUTE_PATTERN else SIGN_IN_ROUTE

    /**
     * Shows the destination for [session], as the only entry of the back stack: Back from the device list after
     * sign-in closes the app instead of returning to sign-in, and signing out pops the device screens, which clears
     * their ViewModels (the device list's and the control screens').
     */
    fun showScreenFor(session: SessionState) {
        val destination = destinationFor(session)
        val current = navController.currentBackStackEntry?.destination?.route
        if (current != null && current != destination) {
            navController.navigate(destination) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}
