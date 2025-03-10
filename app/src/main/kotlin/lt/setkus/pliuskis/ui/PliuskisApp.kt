package lt.setkus.pliuskis.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lt.setkus.pliuskis.R
import lt.setkus.pliuskis.core.designsystem.component.PliuskisBackground
import lt.setkus.pliuskis.core.designsystem.component.PliuskisGradientBackground
import lt.setkus.pliuskis.data.util.NetworkConnectivity
import lt.setkus.pliuskis.navigation.PliuskisNavHost
import timber.log.Timber

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PliuskisApp(
    connectivityMonitor: NetworkConnectivity,
    appState: PliuskisAppState = rememberPliuskisAppState(connectivityMonitor)
) {
    PliuskisBackground {
        PliuskisGradientBackground {
            val snackbarHostState = remember { SnackbarHostState() }
            val isOffline by appState.isOffline.collectAsStateWithLifecycle()
            val notConnectedMessage = stringResource(R.string.not_network_connected)
            LaunchedEffect(isOffline) {
                if (isOffline) {
                    val result = snackbarHostState.showSnackbar(
                        message = notConnectedMessage,
                        duration = SnackbarDuration.Indefinite
                    )
                    Timber.d("Snackbar result: $result")
                }
            }

            Scaffold(
                topBar = { PliuskisTopAppBar() },
                modifier = Modifier.semantics { testTagsAsResourceId = true },
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { padding ->
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Horizontal
                            )
                        )
                ) {
                    Column(Modifier.fillMaxSize()) {
                        PliuskisNavHost(appState = appState, onShowSnackbar = { message, action ->
                            snackbarHostState.showSnackbar(
                                message = message,
                                actionLabel = action,
                                duration = SnackbarDuration.Short,
                            ) == SnackbarResult.ActionPerformed
                        })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PliuskisTopAppBar() = TopAppBar(
    title = {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Pliuškis",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
        }
    },
    actions = {
        IconButton(onClick = { Timber.d("Notifications clicked") }) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Notifications"
            )
        }
        IconButton(onClick = { Timber.d("Settings clicked") }) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings"
            )
        }
    }
)
