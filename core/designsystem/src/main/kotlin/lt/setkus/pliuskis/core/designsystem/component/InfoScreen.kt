package lt.setkus.pliuskis.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import lt.setkus.pliuskis.core.designsystem.theme.Spacing

private val IconContainerSize = 80.dp
private val IconSize = 40.dp
private val MaxTextWidth = 360.dp

/** Color of the icon circle on an [InfoScreen]. */
enum class InfoTone { Neutral, Error }

/**
 * Full-screen message: an [icon] in a tinted circle, a [title], a [message] and, when both [actionLabel] and
 * [onAction] are given, a button that calls [onAction]. Used for empty and error states. Centered when it fits;
 * scrolls in short windows so the action stays reachable.
 */
@Composable
@Suppress("LongParameterList")
fun InfoScreen(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tone: InfoTone = InfoTone.Neutral,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        InfoContent(
            icon = icon,
            tone = tone,
            title = title,
            message = message,
            actionLabel = actionLabel,
            onAction = onAction,
            // At least the window's height, so content that fits stays centered.
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun InfoContent(
    icon: ImageVector,
    tone: InfoTone,
    title: String,
    message: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        InfoIcon(icon = icon, tone = tone)
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = MaxTextWidth)
                .semantics { heading() }
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = MaxTextWidth)
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(Spacing.xl))
            Button(onClick = onAction, modifier = Modifier.heightIn(min = Spacing.minTouchTarget)) {
                Text(text = actionLabel)
            }
        }
    }
}

@Composable
private fun InfoIcon(icon: ImageVector, tone: InfoTone) {
    val (container: Color, content: Color) = when (tone) {
        InfoTone.Neutral -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        InfoTone.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Box(
        modifier = Modifier
            .size(IconContainerSize)
            .background(color = container, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Decorative: the title already says what this screen is about.
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(IconSize)
        )
    }
}
