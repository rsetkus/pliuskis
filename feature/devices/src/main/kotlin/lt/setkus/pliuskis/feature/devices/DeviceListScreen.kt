package lt.setkus.pliuskis.feature.devices

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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
        modifier = modifier
    )
}

@Composable
internal fun DeviceListScreen(
    onDeviceClick: (String) -> Unit,
    devices: DevicesListScreenState,
    modifier: Modifier = Modifier
) {
    val itemsList = remember { mutableListOf<DeviceListItem>() }

    when (devices) {
        is DevicesListScreenState.Error -> ErrorDevicesScreen(
            errorMessage = devices.error.message ?: "No message"
        ) {
            Timber.d("Error: ${devices.error}")
        }
        DevicesListScreenState.Loading -> LoadingDevicesScreen(modifier)
        is DevicesListScreenState.Success -> {
            itemsList.add(devices.device)
            Box(
                modifier = modifier
                    .fillMaxSize()
            ) {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(300.dp)
                ) {
                    devicesFeed(
                        devices = itemsList,
                        onDeviceClick
                    )
                }
            }
        }
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

@Composable
private fun ErrorDevicesScreen(errorMessage: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Use a basic warning symbol if icons are not working
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "!",
                color = MaterialTheme.colorScheme.error,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Connection Error",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = errorMessage,
            fontSize = 16.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(text = "Retry Connection")
        }
    }
}

fun LazyStaggeredGridScope.devicesFeed(
    devices: List<DeviceListItem>,
    onDeviceClick: (String) -> Unit,
) {
    items(
        items = devices,
        key = { it.hashCode() },
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
                    }
                }
            }
        }
    }
}
