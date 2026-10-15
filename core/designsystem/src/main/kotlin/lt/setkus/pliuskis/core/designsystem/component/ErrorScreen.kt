package lt.setkus.pliuskis.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val ScreenPadding = 16.dp
private val IconContainerSize = 80.dp
private val IconSize = 48.dp
private val IconToTitleSpacing = 24.dp
private val TitleToMessageSpacing = 12.dp
private val MessageToActionSpacing = 32.dp
private const val ICON_CONTAINER_ALPHA = 0.1f

/**
 * Full-screen error state: an error icon, a [title], a [message] and, when both [actionLabel] and
 * [onAction] are given, a button that calls [onAction]. Knows nothing about what failed; callers
 * pass in user-facing texts.
 */
@Composable
fun ErrorScreen(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ErrorIcon()
        Spacer(modifier = Modifier.height(IconToTitleSpacing))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(TitleToMessageSpacing))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(MessageToActionSpacing))
            Button(onClick = onAction) {
                Text(text = actionLabel)
            }
        }
    }
}

@Composable
private fun ErrorIcon() {
    Box(
        modifier = Modifier
            .size(IconContainerSize)
            .background(
                color = MaterialTheme.colorScheme.error.copy(alpha = ICON_CONTAINER_ALPHA),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        // Decorative: the title already says what went wrong.
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(IconSize)
        )
    }
}
