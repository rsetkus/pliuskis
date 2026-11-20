package lt.setkus.pliuskis.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import lt.setkus.pliuskis.core.designsystem.icon.PliuskisIcons
import lt.setkus.pliuskis.core.designsystem.theme.Spacing

private val BannerIconSize = 20.dp

/**
 * Persistent banner at the top of the app while there is no internet connection. Draws behind the status bar and pads
 * itself below it, so the caller should consume the status bar inset for the content underneath.
 */
@Composable
fun OfflineBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
        ) {
            Icon(
                imageVector = PliuskisIcons.Offline,
                contentDescription = null,
                modifier = Modifier.size(BannerIconSize)
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
