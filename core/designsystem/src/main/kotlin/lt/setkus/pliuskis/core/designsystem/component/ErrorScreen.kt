package lt.setkus.pliuskis.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import lt.setkus.pliuskis.core.designsystem.icon.PliuskisIcons

/**
 * Full-screen error state: a warning icon, a [title], a [message] and, when both [actionLabel] and [onAction] are
 * given, a button that calls [onAction]. Knows nothing about what failed; callers pass in user-facing texts.
 * Centered when it fits; scrolls in short windows so the action stays reachable.
 */
@Composable
fun ErrorScreen(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    InfoScreen(
        icon = PliuskisIcons.Warning,
        title = title,
        message = message,
        modifier = modifier,
        tone = InfoTone.Error,
        actionLabel = actionLabel,
        onAction = onAction
    )
}
