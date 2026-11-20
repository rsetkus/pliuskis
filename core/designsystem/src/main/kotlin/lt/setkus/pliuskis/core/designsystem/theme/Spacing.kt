@file:Suppress("MagicNumber")

package lt.setkus.pliuskis.core.designsystem.theme

import androidx.compose.ui.unit.dp

/** 4/8 dp spacing scale. Use these instead of raw dp values for padding and gaps. */
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp

    /** Distance from the screen edge to content. */
    val screenMargin = md

    /** Minimum size of anything tappable. */
    val minTouchTarget = 48.dp
}
