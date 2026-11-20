package lt.setkus.pliuskis.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import lt.setkus.pliuskis.core.designsystem.theme.PliuskisTheme
import lt.setkus.pliuskis.data.util.NetworkConnectivity
import lt.setkus.pliuskis.ui.PliuskisApp
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val networkConnectivity by inject<NetworkConnectivity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The splash screen no longer waits for MQTT: DevicesViewModel connects and shows loading itself.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            val darkTheme = isSystemInDarkTheme()

            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim,
                        darkScrim,
                    ) { darkTheme },
                )
                onDispose {}
            }

            PliuskisTheme(darkTheme = darkTheme) {
                // Koin 4 finds the started Koin context itself; the KoinAndroidContext wrapper is no longer needed.
                PliuskisApp(networkConnectivity)
            }
        }
    }
}

private const val LIGHT_ALPHA = 0xe6
private const val LIGHT_RGB = 0xFF

/**
 * The default light scrim, as defined by androidx and the platform:
 * https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:activity/activity/src/main/java/androidx/activity/EdgeToEdge.kt;l=35-38;drc=27e7d52e8604a080133e8b842db10c89b4482598
 */
private val lightScrim = android.graphics.Color.argb(LIGHT_ALPHA, LIGHT_RGB, LIGHT_RGB, LIGHT_RGB)

private const val DARK_ALPHA = 0x80
private const val DARK_RGB = 0x1b

/**
 * The default dark scrim, as defined by androidx and the platform:
 * https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:activity/activity/src/main/java/androidx/activity/EdgeToEdge.kt;l=40-44;drc=27e7d52e8604a080133e8b842db10c89b4482598
 */
private val darkScrim = android.graphics.Color.argb(DARK_ALPHA, DARK_RGB, DARK_RGB, DARK_RGB)
