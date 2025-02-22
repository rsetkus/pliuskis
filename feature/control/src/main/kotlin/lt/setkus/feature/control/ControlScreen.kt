package lt.setkus.feature.control

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun ControlRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ControlViewModel = koinViewModel(),
) {
    val controlUiState: ControlUiState by viewModel.controlUiState.collectAsStateWithLifecycle()

    ControlScreen(
        controlUiState = controlUiState,
        onBackClick = onBackClick,
        modifier = modifier,
        onWaterButtonClick = viewModel::waterPlant
    )
}

@Composable
internal fun ControlScreen(
    controlUiState: ControlUiState,
    onBackClick: () -> Unit,
    onWaterButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (controlUiState) {
        is ControlUiState.Loading -> {
            Text(text = "Loading")
        }
        is ControlUiState.Error -> Text(text = "Error")
        is ControlUiState.Success -> ControlDeviceStatus(controlUiState.data)
    }
}

@Composable
internal fun ControlDeviceStatus(
    data: DeviceStatus,
    onWaterButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = Modifier
    ) {
        Text(text = data.humidity)
        Text(text = data.waterLevel)
        Text(text = data.updatedTime)
        Button(onClick = onWaterButtonClick) {
            Text(text = "Add water")
        }
    }
}