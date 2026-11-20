package lt.setkus.feature.control

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lt.setkus.pliuskis.core.designsystem.component.ErrorScreen
import lt.setkus.pliuskis.core.designsystem.component.PliuskisLoading
import lt.setkus.pliuskis.core.designsystem.component.PliuskisTopAppBar
import lt.setkus.pliuskis.core.designsystem.component.StatusTile
import lt.setkus.pliuskis.core.designsystem.component.StatusTone
import lt.setkus.pliuskis.core.designsystem.icon.PliuskisIcons
import lt.setkus.pliuskis.core.designsystem.theme.Spacing
import lt.setkus.pliuskis.core.presentation.control.ControlUiState
import lt.setkus.pliuskis.core.presentation.control.ControlViewModel
import lt.setkus.pliuskis.core.presentation.control.DeviceStatus
import lt.setkus.pliuskis.core.presentation.control.HumidityStatus
import lt.setkus.pliuskis.core.presentation.control.WaterLevelStatus
import lt.setkus.pliuskis.core.presentation.control.WateringState
import lt.setkus.pliuskis.feature.control.R
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import timber.log.Timber
import kotlin.time.Instant

private val WaterButtonHeight = 56.dp
private val ButtonIconSize = 20.dp
private val ButtonProgressStroke = 2.dp
private val HealthIconSize = 32.dp
private const val PERCENT = 100f

/** Wider than this, the content stops growing and stays centered (Material 3 guidance for wide windows). */
private val MaxContentWidth = 840.dp

/**
 * The control screen of one device, for the detail pane of the device list. [deviceName] is the title (a fallback is
 * shown while it is empty). The back arrow shows only when [showBack] is true, i.e. when the list isn't visible.
 */
@Composable
fun ControlPane(
    deviceId: String,
    deviceName: String,
    showBack: Boolean,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // One ViewModel per device, so switching devices in a two-pane layout doesn't mix their state.
    val viewModel: ControlViewModel = koinViewModel(key = "control-$deviceId") { parametersOf(deviceId) }
    val controlUiState: ControlUiState by viewModel.controlUiState.collectAsStateWithLifecycle()
    val wateringState: WateringState by viewModel.wateringState.collectAsStateWithLifecycle()

    ControlScreen(
        deviceName = deviceName,
        controlUiState = controlUiState,
        wateringState = wateringState,
        onBackClick = onBackClick.takeIf { showBack },
        onWaterButtonClick = viewModel::waterPlant,
        onRetry = viewModel::retry,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
internal fun ControlScreen(
    deviceName: String,
    controlUiState: ControlUiState,
    wateringState: WateringState,
    onBackClick: (() -> Unit)?,
    onWaterButtonClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    updatedAtText: (Instant) -> String = remember { UpdatedAtFormatter() },
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        PliuskisTopAppBar(
            title = deviceName.ifBlank { stringResource(id = R.string.control_title_fallback) },
            onBackClick = onBackClick,
            backContentDescription = stringResource(id = R.string.control_back),
            scrollBehavior = scrollBehavior
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            val content = Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxSize()
            when (controlUiState) {
                is ControlUiState.Loading -> PliuskisLoading(
                    contentDescription = stringResource(id = R.string.control_loading),
                    modifier = content.testTag("control:loading")
                )
                is ControlUiState.Error -> ControlErrorState(
                    message = controlUiState.message,
                    onRetry = onRetry,
                    modifier = content
                )
                is ControlUiState.Success -> PlantStatus(
                    status = controlUiState.data,
                    wateringState = wateringState,
                    onWaterButtonClick = onWaterButtonClick,
                    modifier = content,
                    updatedAtText = updatedAtText
                )
            }
        }
    }
}

/** The connection failed: user-facing texts and a Retry action. The error message itself is only logged. */
@Composable
private fun ControlErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(message) {
        Timber.w("Control screen failed: %s", message)
    }
    ErrorScreen(
        title = stringResource(id = R.string.control_error_title),
        message = stringResource(id = R.string.control_error_message),
        actionLabel = stringResource(id = R.string.control_retry),
        onAction = onRetry,
        modifier = modifier
    )
}

@Composable
internal fun PlantStatus(
    status: DeviceStatus,
    wateringState: WateringState,
    onWaterButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
    updatedAtText: (Instant) -> String = remember { UpdatedAtFormatter() },
) {
    val thirsty = status.humidity == HumidityStatus.THIRSTY
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin)
        ) {
            HealthCard(thirsty = thirsty, updatedAt = updatedAtText(status.updatedAt))
            Spacer(modifier = Modifier.height(Spacing.lg))
            Text(
                text = stringResource(id = R.string.control_conditions),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Conditions(status = status)
            Spacer(modifier = Modifier.height(Spacing.md))
        }
        WateringAction(
            wateringState = wateringState,
            onWaterButtonClick = onWaterButtonClick,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(Spacing.screenMargin)
        )
    }
}

/** The one thing to notice first: is the plant fine or thirsty. Colors animate when the state flips. */
@Composable
private fun HealthCard(thirsty: Boolean, updatedAt: String) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (thirsty) colors.tertiaryContainer else colors.primaryContainer,
        label = "healthContainer"
    )
    val content by animateColorAsState(
        targetValue = if (thirsty) colors.onTertiaryContainer else colors.onPrimaryContainer,
        label = "healthContent"
    )
    val accent by animateColorAsState(
        targetValue = if (thirsty) colors.tertiary else colors.primary,
        label = "healthAccent"
    )
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Icon(
                imageVector = if (thirsty) PliuskisIcons.Thirsty else PliuskisIcons.Plant,
                contentDescription = null,
                modifier = Modifier.size(HealthIconSize)
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = stringResource(
                    id = if (thirsty) R.string.control_thirsty_title else R.string.control_thriving_title
                ),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(Spacing.xxs))
            Text(
                text = stringResource(
                    id = if (thirsty) R.string.control_thirsty_message else R.string.control_thriving_message
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            Text(
                text = stringResource(id = R.string.control_updated, updatedAt),
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
        }
    }
}

@Composable
private fun Conditions(status: DeviceStatus) {
    val dry = status.humidity == HumidityStatus.THIRSTY
    val lowTank = status.waterLevel == WaterLevelStatus.LOW
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        StatusTile(
            icon = PliuskisIcons.SoilMoisture,
            label = stringResource(id = R.string.control_moisture),
            value = stringResource(id = R.string.control_percent, status.soilMoisturePercent),
            caption = stringResource(id = if (dry) R.string.control_moisture_low else R.string.control_moisture_ok),
            tone = if (dry) StatusTone.Attention else StatusTone.Positive,
            progress = status.soilMoisturePercent / PERCENT,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        StatusTile(
            icon = PliuskisIcons.Water,
            label = stringResource(id = R.string.control_tank),
            value = stringResource(id = R.string.control_percent, status.waterLevelPercent),
            caption = stringResource(id = if (lowTank) R.string.control_tank_low else R.string.control_tank_ok),
            tone = if (lowTank) StatusTone.Attention else StatusTone.Informative,
            progress = status.waterLevelPercent / PERCENT,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

/** "Water now", pinned to the bottom of the screen where the thumb rests. */
@Composable
private fun WateringAction(
    wateringState: WateringState,
    onWaterButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(visible = wateringState == WateringState.FAILED) {
            Text(
                text = stringResource(id = R.string.control_watering_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.sm)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        Button(
            onClick = { if (wateringState != WateringState.SENDING) onWaterButtonClick() },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = WaterButtonHeight)
        ) {
            AnimatedContent(
                targetState = wateringState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "watering"
            ) { state ->
                WaterButtonContent(state)
            }
        }
    }
}

@Composable
private fun WaterButtonContent(state: WateringState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        if (state == WateringState.SENDING) {
            CircularProgressIndicator(
                color = LocalContentColor.current,
                strokeWidth = ButtonProgressStroke,
                modifier = Modifier.size(ButtonIconSize)
            )
        } else {
            Icon(
                imageVector = if (state == WateringState.STARTED) PliuskisIcons.Done else PliuskisIcons.Water,
                contentDescription = null,
                modifier = Modifier.size(ButtonIconSize)
            )
        }
        Spacer(modifier = Modifier.width(Spacing.xs))
        Text(
            text = stringResource(
                id = when (state) {
                    WateringState.IDLE -> R.string.control_water_now
                    WateringState.SENDING -> R.string.control_watering
                    WateringState.STARTED -> R.string.control_watering_started
                    WateringState.FAILED -> R.string.control_try_again
                }
            ),
            style = MaterialTheme.typography.labelLarge
        )
    }
}
