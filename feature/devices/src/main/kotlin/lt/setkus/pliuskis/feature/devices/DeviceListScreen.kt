package lt.setkus.pliuskis.feature.devices

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val gridState = rememberLazyGridState()
    val itemsList = remember { mutableListOf<DeviceListItem>() }

    when (devices) {
        is DevicesListScreenState.Error -> Timber.e("Error: ${devices.error}")
        DevicesListScreenState.Loading -> Unit
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
                        onDeviceClick,
                        modifier = modifier
                    )
                }
            }

        }
    }
}

fun LazyStaggeredGridScope.devicesFeed(
    devices: List<DeviceListItem>,
    onDeviceClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    items(
        items = devices,
        key = { it.hashCode() },
        contentType = { "deviceList" }
    ) {device ->
        DeviceCard(
            device,
            onDeviceClick,
            modifier = modifier
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
    Card(
        onClick = { onDeviceClick(device.id) },
        modifier = modifier
    ) {
        Column {
            Box {
                Column {
                    Row {
                        Text(
                            text = device.name
                        )
                    }
                }
            }
        }
    }
}