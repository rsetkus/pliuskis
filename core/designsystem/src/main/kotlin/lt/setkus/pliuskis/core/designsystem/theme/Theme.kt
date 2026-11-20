package lt.setkus.pliuskis.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LightFloraColorScheme = lightColorScheme(
    primary = Leaf40,
    onPrimary = Color.White,
    primaryContainer = Leaf90,
    onPrimaryContainer = Leaf10,
    inversePrimary = Leaf80,
    secondary = Rain40,
    onSecondary = Color.White,
    secondaryContainer = Rain90,
    onSecondaryContainer = Rain10,
    tertiary = Clay40,
    onTertiary = Color.White,
    tertiaryContainer = Clay90,
    onTertiaryContainer = Clay10,
    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = Moss98,
    onBackground = Moss10,
    surface = Moss98,
    onSurface = Moss10,
    surfaceVariant = MossVariant90,
    onSurfaceVariant = MossVariant30,
    surfaceDim = Moss87,
    surfaceBright = Moss98,
    surfaceContainerLowest = Moss100,
    surfaceContainerLow = Moss96,
    surfaceContainer = Moss94,
    surfaceContainerHigh = Moss92,
    surfaceContainerHighest = Moss90,
    surfaceTint = Leaf40,
    inverseSurface = Moss20,
    inverseOnSurface = Moss95,
    outline = MossVariant50,
    outlineVariant = MossVariant80,
)

val DarkFloraColorScheme = darkColorScheme(
    primary = Leaf80,
    onPrimary = Leaf20,
    primaryContainer = Leaf30,
    onPrimaryContainer = Leaf90,
    inversePrimary = Leaf40,
    secondary = Rain80,
    onSecondary = Rain20,
    secondaryContainer = Rain30,
    onSecondaryContainer = Rain90,
    tertiary = Clay80,
    onTertiary = Clay20,
    tertiaryContainer = Clay30,
    onTertiaryContainer = Clay90,
    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = Moss6,
    onBackground = Moss90,
    surface = Moss6,
    onSurface = Moss90,
    surfaceVariant = MossVariant30,
    onSurfaceVariant = MossVariant80,
    surfaceDim = Moss6,
    surfaceBright = Moss24,
    surfaceContainerLowest = Moss4,
    surfaceContainerLow = Moss10,
    surfaceContainer = Moss12,
    surfaceContainerHigh = Moss17,
    surfaceContainerHighest = Moss22,
    surfaceTint = Leaf80,
    inverseSurface = Moss90,
    inverseOnSurface = Moss20,
    outline = MossVariant60,
    outlineVariant = MossVariant30,
)

/**
 * Pliuškis Flora theme. Brand colors always apply: there is no dynamic (wallpaper) color, so the plant-health
 * colors mean the same thing on every device. Follows the system light/dark setting by default.
 */
@Composable
fun PliuskisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkFloraColorScheme else LightFloraColorScheme,
        typography = PliuskisTypography,
        content = content
    )
}
