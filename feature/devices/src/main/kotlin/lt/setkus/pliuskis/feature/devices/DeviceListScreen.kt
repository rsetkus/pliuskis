package lt.setkus.pliuskis.feature.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lt.setkus.pliuskis.core.designsystem.component.ErrorScreen
import lt.setkus.pliuskis.core.designsystem.component.InfoScreen
import lt.setkus.pliuskis.core.designsystem.component.PliuskisLargeTopAppBar
import lt.setkus.pliuskis.core.designsystem.component.PliuskisLoading
import lt.setkus.pliuskis.core.designsystem.icon.PliuskisIcons
import lt.setkus.pliuskis.core.designsystem.theme.Spacing
import lt.setkus.pliuskis.core.devicelist.DeviceType
import lt.setkus.pliuskis.core.presentation.devices.DeviceListItem
import lt.setkus.pliuskis.core.presentation.devices.DevicesListScreenState
import timber.log.Timber

private val DeviceIconContainerSize = 40.dp
private val DeviceIconSize = 24.dp
private val StatusDotSize = 8.dp

/**
 * The device list ("Your garden"). In a two-pane layout ([twoPane]) the [selectedDeviceId] row is highlighted and rows
 * drop their chevron, because the detail is already beside the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
internal fun DeviceListScreen(
    onDeviceClick: (id: String, name: String) -> Unit,
    devices: DevicesListScreenState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    selectedDeviceId: String? = null,
    twoPane: Boolean = false,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        PliuskisLargeTopAppBar(
            title = stringResource(id = R.string.devices_title),
            scrollBehavior = scrollBehavior
        )
        Box(modifier = Modifier.weight(1f)) {
            when (devices) {
                is DevicesListScreenState.Error -> ErrorDevicesState(error = devices.error, onRetry = onRetry)
                DevicesListScreenState.Loading -> PliuskisLoading(
                    contentDescription = stringResource(id = R.string.devices_loading),
                    modifier = Modifier.testTag("devices:loading")
                )
                is DevicesListScreenState.Success -> if (devices.devices.isEmpty()) {
                    EmptyDevicesScreen(onSearchAgain = onRetry)
                } else {
                    DevicesList(
                        devices = devices.devices,
                        onDeviceClick = onDeviceClick,
                        selectedDeviceId = selectedDeviceId.takeIf { twoPane },
                        showChevron = !twoPane
                    )
                }
            }
        }
    }
}

@Composable
private fun DevicesList(
    devices: List<DeviceListItem>,
    onDeviceClick: (id: String, name: String) -> Unit,
    selectedDeviceId: String?,
    showChevron: Boolean,
) {
    // The list scrolls behind the navigation bar, so its last row can still scroll above it.
    val bottomBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.screenMargin,
            end = Spacing.screenMargin,
            bottom = Spacing.screenMargin + bottomBar
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        item(key = "summary", contentType = "summary") {
            Text(
                text = pluralStringResource(
                    id = R.plurals.devices_summary,
                    count = devices.size,
                    devices.size,
                    devices.count { it.online }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.xs)
            )
        }
        items(
            items = devices,
            key = { it.id },
            contentType = { "device" }
        ) { device ->
            DeviceRow(
                device = device,
                selected = device.id == selectedDeviceId,
                showChevron = showChevron,
                onClick = { onDeviceClick(device.id, device.name) }
            )
        }
    }
}

/** Nothing found yet: says how to make a device appear and offers to search again. */
@Composable
private fun EmptyDevicesScreen(onSearchAgain: () -> Unit) {
    InfoScreen(
        icon = PliuskisIcons.Plant,
        title = stringResource(id = R.string.devices_empty_title),
        message = stringResource(id = R.string.devices_empty),
        actionLabel = stringResource(id = R.string.devices_search_again),
        onAction = onSearchAgain
    )
}

/**
 * Discovery failed: shows the generic error screen with user-facing texts and a Retry action. The
 * exception itself is only logged, never shown.
 */
@Composable
private fun ErrorDevicesState(error: Throwable, onRetry: () -> Unit) {
    LaunchedEffect(error) {
        Timber.w(error, "Device discovery failed")
    }
    ErrorScreen(
        title = stringResource(id = R.string.devices_error_title),
        message = stringResource(id = R.string.devices_error_message),
        actionLabel = stringResource(id = R.string.devices_retry),
        onAction = onRetry
    )
}

/** One device as a Material 3 list item on a `surfaceContainerHigh` row; `secondaryContainer` when selected. */
@Composable
internal fun DeviceRow(
    device: DeviceListItem,
    selected: Boolean,
    showChevron: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val rowColor = if (selected) colors.secondaryContainer else colors.surfaceContainerHigh
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = rowColor,
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
    ) {
        ListItem(
            leadingContent = { DeviceIcon(type = device.type, online = device.online) },
            headlineContent = {
                Text(text = device.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = { AvailabilityLabel(online = device.online) },
            trailingContent = if (showChevron) {
                { Icon(imageVector = PliuskisIcons.Forward, contentDescription = null) }
            } else {
                null
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
private fun AvailabilityLabel(online: Boolean) {
    // Offline uses onSurfaceVariant, not a faded color, so the label keeps 4.5:1 contrast.
    val dotColor = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val textColor = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(StatusDotSize)
                .background(dotColor, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(Spacing.xs))
        Text(
            text = stringResource(id = if (online) R.string.device_online else R.string.device_offline),
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}

@Composable
private fun DeviceIcon(type: DeviceType, online: Boolean) {
    val (icon: ImageVector, description: String) = when (type) {
        DeviceType.PLANT -> PliuskisIcons.Plant to stringResource(id = R.string.device_type_plant)
        DeviceType.UNKNOWN -> PliuskisIcons.UnknownDevice to stringResource(id = R.string.device_type_unknown)
    }
    val colors = MaterialTheme.colorScheme
    val container = if (online) colors.primaryContainer else colors.surfaceVariant
    val content = if (online) colors.onPrimaryContainer else colors.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(DeviceIconContainerSize)
            .background(container, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = content,
            modifier = Modifier.size(DeviceIconSize)
        )
    }
}
