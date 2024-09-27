package lt.setkus.pliuskis.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import lt.setkus.pliuskis.main.MainContract.Intent.RequestSystemUpdate
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainActivityViewModel by viewModel()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.viewState.observe(this@MainActivity) { state ->
                    Timber.d("State: $state")
                }
            }
        }

        setContent {
            show()
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.setIntent(RequestSystemUpdate)
    }
}

@Preview
@Composable
fun show() {
    FlowerPot()
}

@Composable
fun FlowerPot() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val potColor = Color(0xFFA5694F)
        val rimColor = Color(0xFF8B4513)
        val soilColor = Color(0xFF382119)
        val waterColor = Color(0xFF4287f5) // Light blue for water

        // Draw the main body of the pot
        drawRoundRect(
            color = potColor,
            topLeft = Offset(size.width * 0.2f, size.height * 0.15f),
            size = Size(size.width * 0.6f, size.height * 0.6f),
            cornerRadius = CornerRadius(size.width * 0.1f, size.width * 0.1f)
        )

        // Draw the rim of the pot
        drawRoundRect(
            color = rimColor,
            topLeft = Offset(size.width * 0.15f, size.height * 0.1f),
            size = Size(size.width * 0.7f, size.height * 0.1f),
            cornerRadius = CornerRadius(size.width * 0.1f, size.width * 0.1f)
        )

        // Draw the soil inside the pot
        drawRect(
            color = soilColor,
            topLeft = Offset(size.width * 0.25f, size.height * 0.27f),
            size = Size(size.width * 0.5f, size.height * 0.45f)
        )

        // Draw the water level (adjust height as needed)
        drawRect(
            color = waterColor,
            topLeft = Offset(size.width * 0.25f, size.height * 0.27f),
            size = Size(size.width * 0.5f, size.height * 0.45f)
        )
    }
}

