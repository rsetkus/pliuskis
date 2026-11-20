package lt.setkus.pliuskis.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lt.setkus.pliuskis.core.designsystem.theme.Spacing

private val LabelIconSize = 16.dp
private val ProgressHeight = 6.dp

/** What a reading means, which sets its accent color. */
enum class StatusTone {
    /** Healthy (Leaf). */
    Positive,

    /** Fine, and about water (Rain). */
    Informative,

    /** Needs attention soon (Clay). */
    Attention,
}

/**
 * One sensor reading: a small [icon] and [label], a large [value], an optional [progress] meter (0..1) and a short
 * [caption] that says what the value means ("Healthy", "Dry"). [tone] colors the meter and the caption. Read by
 * screen readers as one item: label, value, caption.
 */
@Composable
@Suppress("LongParameterList")
fun StatusTile(
    icon: ImageVector,
    label: String,
    value: String,
    caption: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    val accent = tone.accentColor()
    Card(
        modifier = modifier.clearAndSetSemantics { contentDescription = "$label, $value, $caption" },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(LabelIconSize)
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            if (progress != null) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ProgressHeight)
                )
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = caption,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatusTone.accentColor(): Color = when (this) {
    StatusTone.Positive -> MaterialTheme.colorScheme.primary
    StatusTone.Informative -> MaterialTheme.colorScheme.secondary
    StatusTone.Attention -> MaterialTheme.colorScheme.tertiary
}
