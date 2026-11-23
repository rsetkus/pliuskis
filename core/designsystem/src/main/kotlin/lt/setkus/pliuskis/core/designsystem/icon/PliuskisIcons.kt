package lt.setkus.pliuskis.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every icon the app uses, all from the Material Rounded set: core icons from `material-icons-core`, the rest
 * copied into RoundedIcons.kt. Pick from here, not from `Icons` directly.
 */
object PliuskisIcons {
    val Back: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack
    val Forward: ImageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight
    val Plant: ImageVector = RoundedEco
    val UnknownDevice: ImageVector = RoundedDeviceUnknown
    val SoilMoisture: ImageVector = RoundedOpacity
    val Water: ImageVector = RoundedWaterDrop
    val Thirsty: ImageVector = RoundedFormatColorReset
    val Done: ImageVector = Icons.Rounded.CheckCircle
    val Warning: ImageVector = Icons.Rounded.Warning
    val Offline: ImageVector = RoundedWifiOff
    val Account: ImageVector = Icons.Rounded.AccountCircle
}
