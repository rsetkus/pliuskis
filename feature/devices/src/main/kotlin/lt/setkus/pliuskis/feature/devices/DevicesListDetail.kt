package lt.setkus.pliuskis.feature.devices

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import lt.setkus.pliuskis.core.designsystem.component.InfoScreen
import lt.setkus.pliuskis.core.designsystem.icon.PliuskisIcons
import lt.setkus.pliuskis.core.presentation.devices.DevicesListScreenState
import lt.setkus.pliuskis.core.presentation.devices.DevicesViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * Draws one device's detail. Called with the device id and name, whether to show a back arrow (only when the list
 * isn't visible) and what back does.
 */
typealias DeviceDetailPane = @Composable (
    deviceId: String,
    deviceName: String,
    showBack: Boolean,
    onBack: () -> Unit
) -> Unit

/**
 * Material 3 list-detail layout. On phones one pane shows at a time and the detail slides over the list; on wide
 * windows (840dp and up) both show side by side. The selected device id survives configuration changes and process
 * death.
 */
@Composable
internal fun DevicesListDetailRoute(
    detailPane: DeviceDetailPane,
    modifier: Modifier = Modifier,
    viewModel: DevicesViewModel = koinViewModel(),
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    DevicesListDetail(
        devices = devices,
        onRetry = viewModel::retry,
        detailPane = detailPane,
        modifier = modifier
    )
}

/**
 * Stateless list-detail. [scaffoldDirective] (how many panes fit) comes from the window size; tests pass their own.
 * [initialSelectedId] opens a device's detail from the start (for tests).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
@Suppress("LongParameterList")
internal fun DevicesListDetail(
    devices: DevicesListScreenState,
    onRetry: () -> Unit,
    detailPane: DeviceDetailPane,
    modifier: Modifier = Modifier,
    scaffoldDirective: PaneScaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo()),
    initialSelectedId: String? = null,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator(
        scaffoldDirective = scaffoldDirective,
        initialDestinationHistory = listOfNotNull(
            ThreePaneScaffoldDestinationItem<String>(ListDetailPaneScaffoldRole.List),
            initialSelectedId?.let { ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.Detail, it) }
        )
    )
    val listVisible = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded
    val detailVisible = navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
    val twoPane = listVisible && detailVisible
    val selectedId = navigator.currentDestination?.contentKey
    val scope = rememberCoroutineScope()

    // Handles system back, with the predictive back animation, between the detail and the list.
    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane {
                DeviceListScreen(
                    onDeviceClick = { id, _ ->
                        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id) }
                    },
                    devices = devices,
                    onRetry = onRetry,
                    selectedDeviceId = selectedId,
                    twoPane = twoPane
                )
            }
        },
        detailPane = {
            AnimatedPane {
                if (selectedId == null) {
                    NoDeviceSelected()
                } else {
                    detailPane(
                        selectedId,
                        devices.nameOf(selectedId),
                        !listVisible,
                        { scope.launch { navigator.navigateBack() } }
                    )
                }
            }
        },
        modifier = modifier
    )
}

/** Wide windows with nothing selected yet. */
@Composable
private fun NoDeviceSelected() {
    InfoScreen(
        icon = PliuskisIcons.Plant,
        title = stringResource(id = R.string.devices_choose_title),
        message = stringResource(id = R.string.devices_choose_message)
    )
}

/** The device's name from the list, or empty while the list is still loading (e.g. right after process death). */
private fun DevicesListScreenState.nameOf(deviceId: String): String =
    (this as? DevicesListScreenState.Success)?.devices?.firstOrNull { it.id == deviceId }?.name.orEmpty()
