package lt.setkus.pliuskis.feature.devices

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lt.setkus.pliuskis.core.designsystem.component.ErrorScreen
import lt.setkus.pliuskis.core.designsystem.component.LoadingWheel
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

@Composable
internal fun DevicesListRoute(
    onDeviceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DevicesViewModel = koinViewModel(),
) {

    val devicesListUiState by viewModel.devices.collectAsStateWithLifecycle()

    DeviceListScreen(
        onDeviceClick = onDeviceClick,
        devices = devicesListUiState,
        onRetry = viewModel::retry,
        modifier = modifier
    )
}

@Composable
internal fun DeviceListScreen(
    onDeviceClick: (String) -> Unit,
    devices: DevicesListScreenState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (devices) {
        is DevicesListScreenState.Error -> ErrorDevicesState(
            error = devices.error,
            onRetry = onRetry,
            modifier = modifier
        )
        DevicesListScreenState.Loading -> LoadingDevicesScreen(modifier)
        is DevicesListScreenState.Success -> if (devices.devices.isEmpty()) {
            EmptyDevicesScreen(modifier)
        } else {
            Box(
                modifier = modifier
                    .fillMaxSize()
            ) {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(300.dp)
                ) {
                    devicesFeed(
                        devices = devices.devices,
                        onDeviceClick
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDevicesScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(id = R.string.devices_empty),
            fontSize = 16.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
@Suppress("MagicNumber", "UnusedPrivateMember")
private fun LoadingDevicesScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LoadingWheel(
                modifier = modifier
                    .fillMaxWidth()
                    .wrapContentSize()
                    .testTag("forYou:loading"),
                contentDesc = stringResource(id = R.string.devices_loading),
            )
        }
    }
}

/**
 * Discovery failed: shows the generic error screen with user-facing texts and a Retry action. The
 * exception itself is only logged, never shown.
 */
@Composable
private fun ErrorDevicesState(error: Throwable, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(error) {
        Timber.w(error, "Device discovery failed")
    }
    ErrorScreen(
        title = stringResource(id = R.string.devices_error_title),
        message = stringResource(id = R.string.devices_error_message),
        modifier = modifier,
        actionLabel = stringResource(id = R.string.devices_retry),
        onAction = onRetry
    )
}

fun LazyStaggeredGridScope.devicesFeed(
    devices: List<DeviceListItem>,
    onDeviceClick: (String) -> Unit,
) {
    items(
        items = devices,
        key = { it.id },
        contentType = { "deviceList" }
    ) { device ->
        DeviceCard(
            device,
            onDeviceClick,
            modifier = Modifier
                .height(100.dp)
                .padding(5.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceCard(
    device: DeviceListItem,
    onDeviceClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = { onDeviceClick(device.id) },
        elevation = CardDefaults
            .cardElevation(defaultElevation = 6.dp),
        modifier = modifier
    ) {
        Column {
            Box {
                Column {
                    Row {
                        Text(
                            text = device.name,
                            modifier = Modifier
                                .padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                        DeviceIcon(icId = device.icon)
                    }
                    AvailabilityLabel(
                        online = device.online,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AvailabilityLabel(
    online: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val label = stringResource(id = if (online) R.string.device_online else R.string.device_offline)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = color
        )
    }
}

@Composable
private fun DeviceIcon(
    modifier: Modifier = Modifier,
    contentDescription: String? = "Unknown",
    @DrawableRes icId: Int
) {
    Icon(
        imageVector = ImageVector.vectorResource(id = icId),
        contentDescription = contentDescription,
        modifier = modifier.size(24.dp)
    )
}
