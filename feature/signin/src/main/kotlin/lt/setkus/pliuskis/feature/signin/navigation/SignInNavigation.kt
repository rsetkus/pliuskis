package lt.setkus.pliuskis.feature.signin.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import lt.setkus.pliuskis.feature.signin.SignInRoute

const val SIGN_IN_ROUTE = "sign_in_route"

/**
 * The sign-in screen. It doesn't navigate on its own: after a successful sign-in the app's session state moves the
 * app to the device list.
 */
fun NavGraphBuilder.signInScreen() {
    composable(route = SIGN_IN_ROUTE) {
        SignInRoute()
    }
}
