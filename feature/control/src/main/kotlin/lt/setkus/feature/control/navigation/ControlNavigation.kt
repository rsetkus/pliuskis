package lt.setkus.feature.control.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import lt.setkus.feature.control.ControlRoute

internal const val CONTROL_ID_ARG = "deviceId"

fun NavController.navigateControl(deviceId: String) {
    navigate("control/$deviceId") {
        launchSingleTop = true
    }
}

fun NavGraphBuilder.controlScreen(
    onBackClick: () -> Unit
) {
    composable(
        route = "control/{$CONTROL_ID_ARG}",
        arguments = listOf(
            navArgument(CONTROL_ID_ARG) { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val deviceId = checkNotNull(backStackEntry.arguments?.getString(CONTROL_ID_ARG)) {
            "The control route has no $CONTROL_ID_ARG argument"
        }
        ControlRoute(deviceId = deviceId, onBackClick = onBackClick)
    }
}
