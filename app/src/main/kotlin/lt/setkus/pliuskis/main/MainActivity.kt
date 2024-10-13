package lt.setkus.pliuskis.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import lt.setkus.pliuskis.core.designsystem.theme.PliuskisTheme
import lt.setkus.pliuskis.main.MainContract.Intent.ConnectToBroker
import lt.setkus.pliuskis.main.MainContract.MainScreenState.Connected
import lt.setkus.pliuskis.main.MainContract.MainScreenState.Loading
import lt.setkus.pliuskis.ui.PliuskisApp
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainActivityViewModel by viewModel()
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.viewState.observe(this@MainActivity) {
                    splashScreen.setKeepOnScreenCondition {
                        when (it.state) {
                            is Loading -> true
                            is Connected -> false
                        }
                    }
                }
            }
        }

        enableEdgeToEdge()

        setContent {
            PliuskisTheme {
                PliuskisApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.setIntent(ConnectToBroker)
    }
}

//@Composable
//fun show(mainViewModel: MainActivityViewModel) {
//
//    val uiState = mainViewModel.viewState.observeAsState(State(Loading))
//
//    val uiStateValue: State = uiState.value
//    when (uiStateValue.state) {
//        is Loading -> "Loading..."
//        is MainContract.MainScreenState.ErrorScreen -> uiStateValue.state.error
//        is MainContract.MainScreenState.SystemStatus -> uiStateValue.state.status
//        Connected -> "Connected"
//    }
//}
//
//@Composable
//fun showStatus() {
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(16.dp),
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.Center
//    ) {
//        Text(
//            text = message,
//            style = MaterialTheme.typography.headlineMedium,
//            modifier = Modifier.align(Alignment.CenterHorizontally)
//        )
//
//        Spacer(modifier = Modifier.height(16.dp))
//
//        Button(
//            onClick = { /*TODO*/ },
//            modifier = Modifier.align(Alignment.CenterHorizontally),
//            enabled = false
//        ) {
//            Text(text = "Request update")
//        }
//    }
//}