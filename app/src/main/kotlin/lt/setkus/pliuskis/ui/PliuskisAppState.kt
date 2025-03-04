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
import lt.setkus.pliuskis.data.util.NetworkConnectivity

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
}
