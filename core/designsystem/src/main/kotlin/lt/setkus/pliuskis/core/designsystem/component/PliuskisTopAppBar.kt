package lt.setkus.pliuskis.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import lt.setkus.pliuskis.core.designsystem.icon.PliuskisIcons

/**
 * Small top app bar for a detail screen. Shows a back arrow when [onBackClick] is given; [backContentDescription] is
 * its screen reader label. Pass a pinned [scrollBehavior] so the bar tints to `surfaceContainer` when content
 * scrolls under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PliuskisTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    backContentDescription: String? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        title = { AppBarTitle(title) },
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = PliuskisIcons.Back, contentDescription = backContentDescription)
                }
            }
        },
        scrollBehavior = scrollBehavior,
        modifier = modifier
    )
}

/**
 * Large top app bar for a top-level screen. Pass an `exitUntilCollapsed` [scrollBehavior] so the title collapses
 * into a small bar as the list scrolls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PliuskisLargeTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    LargeTopAppBar(
        title = { AppBarTitle(title) },
        scrollBehavior = scrollBehavior,
        modifier = modifier
    )
}

@Composable
private fun AppBarTitle(title: String) {
    Text(
        text = title,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics { heading() }
    )
}
