package lt.setkus.pliuskis.feature.devices

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun DevicesListRoute(
    onDeviceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DevicesViewModel = koinViewModel(),
) {
    DeviceListScreen(
        onDeviceClick = onDeviceClick,
        modifier = modifier
    )
}

@Composable
internal fun DeviceListScreen(
    onDeviceClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(300.dp)
        ) {
            devicesFeed(modifier = modifier)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun LazyStaggeredGridScope.devicesFeed(
    modifier: Modifier = Modifier
) {
    items(
        items = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"),
        key = { it },
        contentType = { "deviceList" }
    ) {device ->
        DeviceCard(
            device,
            modifier = modifier
        )
    }
}

@Composable
internal fun DeviceCard(
    devices: String = "",
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
    ) {
        Column {
            Box {
                Column {
                    Row {
                        Text(text = devices)
                    }
                }
            }
        }
    }
}