package lt.setkus.pliuskis.feature.devices

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.window.core.layout.WindowSizeClass
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import lt.setkus.pliuskis.core.designsystem.component.PliuskisBackground
import lt.setkus.pliuskis.core.designsystem.theme.PliuskisTheme
import lt.setkus.pliuskis.core.devicelist.DeviceType
import lt.setkus.pliuskis.core.presentation.devices.DeviceListItem
import lt.setkus.pliuskis.core.presentation.devices.DevicesListScreenState.Success
import org.junit.Rule
import org.junit.Test

private const val TABLET_WIDTH_DP = 1280f
private const val TABLET_HEIGHT_DP = 900f

/** The list-detail layout on a landscape tablet, where list and detail show side by side. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class DevicesListDetailSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_C.copy(orientation = ScreenOrientation.LANDSCAPE))

    private val devices = Success(
        listOf(
            DeviceListItem(name = "Basil", id = "a", type = DeviceType.PLANT, online = true),
            DeviceListItem(name = "Tomato", id = "b", type = DeviceType.UNKNOWN, online = false)
        )
    )

    private fun snapshot(initialSelectedId: String?) {
        paparazzi.snapshot {
            PliuskisTheme(darkTheme = false) {
                PliuskisBackground {
                    DevicesListDetail(
                        devices = devices,
                        onRetry = {},
                        detailPane = { _, name, _, _ ->
                            // The real detail is the control feature's; a stand-in keeps this module independent.
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = "Detail: $name", style = MaterialTheme.typography.titleLarge)
                            }
                        },
                        // Paparazzi doesn't report window metrics to the adaptive library, so give it the tablet's.
                        scaffoldDirective = calculatePaneScaffoldDirective(
                            WindowAdaptiveInfo(WindowSizeClass.compute(TABLET_WIDTH_DP, TABLET_HEIGHT_DP), Posture())
                        ),
                        initialSelectedId = initialSelectedId
                    )
                }
            }
        }
    }

    @Test
    fun tabletShowsListAndPlaceholderWhenNothingIsSelected() {
        snapshot(initialSelectedId = null)
    }

    @Test
    fun tabletShowsListAndSelectedDeviceSideBySide() {
        snapshot(initialSelectedId = "a")
    }
}
