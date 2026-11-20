package lt.setkus.pliuskis.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lt.setkus.pliuskis.R
import lt.setkus.pliuskis.core.designsystem.component.OfflineBanner
import lt.setkus.pliuskis.core.designsystem.component.PliuskisBackground
import lt.setkus.pliuskis.data.util.NetworkConnectivity
import lt.setkus.pliuskis.navigation.PliuskisNavHost

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PliuskisApp(
    connectivityMonitor: NetworkConnectivity,
    appState: PliuskisAppState = rememberPliuskisAppState(connectivityMonitor)
) {
    PliuskisBackground {
        val snackbarHostState = remember { SnackbarHostState() }
        val isOffline by appState.isOffline.collectAsStateWithLifecycle()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // A persistent banner rather than a snackbar: being offline is a state, and a snackbar would cover
                // the screen's bottom action.
                AnimatedVisibility(visible = isOffline, enter = expandVertically(), exit = shrinkVertically()) {
                    OfflineBanner(message = stringResource(R.string.not_network_connected))
                }
                PliuskisNavHost(
                    appState = appState,
                    onShowSnackbar = { message, action ->
                        snackbarHostState.showSnackbar(
                            message = message,
                            actionLabel = action,
                            duration = SnackbarDuration.Short,
                        ) == SnackbarResult.ActionPerformed
                    },
                    // The banner already sits under the status bar, so the screens' top bars mustn't pad for it again.
                    modifier = if (isOffline) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier
                )
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            )
        }
    }
}
