package lt.setkus.pliuskis.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import lt.setkus.pliuskis.core.testing.PliuskisTestApplication
import lt.setkus.pliuskis.core.testing.util.TestNetworkConnectivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests [PliuskisAppState]
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = PliuskisTestApplication::class)
class PliuskisAppStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Create the test dependencies.
    private val networkConnectivity = TestNetworkConnectivity()

    @Test
    fun testPliuskisAppStateCurrentDestination() = runTest {
        var currentDestination: String? = null

        composeTestRule.setContent {
            val navController = rememberTestNavController()
            val actualState = remember(navController) {
                PliuskisAppState(
                    navController = navController,
                    coroutineScope = backgroundScope,
                    connectivity = networkConnectivity
                )
            }

            currentDestination = actualState.currentDestination?.route

            // Navigate to destination b once
            LaunchedEffect(Unit) {
                navController.setCurrentDestination("b")
            }
        }

        assertEquals("b", currentDestination)
    }

    @Test
    fun testPliuskisAppOfflineState() = runTest(UnconfinedTestDispatcher()) {
        var appState: PliuskisAppState? = null
        composeTestRule.setContent {
            appState = PliuskisAppState(
                navController = NavHostController(LocalContext.current),
                coroutineScope = backgroundScope,
                connectivity = networkConnectivity
            )
        }

        backgroundScope.launch { appState?.isOffline?.collect() }
        networkConnectivity.setConnected(false)

        assertTrue {
            appState?.isOffline?.value ?: false
        }
    }
}

@Composable
private fun rememberTestNavController(): TestNavHostController {
    val context = LocalContext.current
    return remember {
        TestNavHostController(context).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            graph = createGraph(startDestination = "a") {
                composable("a") { }
                composable("b") { }
                composable("c") { }
            }
        }
    }
}
