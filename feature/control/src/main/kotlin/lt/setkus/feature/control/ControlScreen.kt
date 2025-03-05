package lt.setkus.feature.control

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import lt.setkus.pliuskis.feature.control.R
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

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
        is ControlUiState.Loading -> LoadingControlScreen(modifier)
        is ControlUiState.Error -> ErrorDevicesScreen(
            controlUiState.message,
        ) {
            Timber.d("Error: ${controlUiState.message}")
        }
        is ControlUiState.Success -> ControlDeviceStatus(controlUiState.data, onWaterButtonClick)
    }
}

@Composable
internal fun LoadingControlScreen(modifier: Modifier = Modifier) {
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
                contentDesc = stringResource(id = R.string.control_loading),
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
