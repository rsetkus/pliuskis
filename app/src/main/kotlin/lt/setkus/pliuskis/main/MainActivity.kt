package lt.setkus.pliuskis.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lt.setkus.pliuskis.main.MainContract.Intent.RequestSystemUpdate
import lt.setkus.pliuskis.main.MainContract.MainScreenState.Loading
import lt.setkus.pliuskis.main.MainContract.State
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainActivityViewModel by viewModel()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            show(mainViewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.setIntent(RequestSystemUpdate("pliuskis"))
    }
}

@Composable
fun show(mainViewModel: MainActivityViewModel) {

    val uiState = mainViewModel.viewState.observeAsState(State(Loading))
    Timber.d("State in compose: $uiState")
    val uiStateValue: State = uiState.value
    val message = when (uiStateValue.state) {
        is Loading -> "Loading..."
        is MainContract.MainScreenState.ErrorScreen -> uiStateValue.state.error
        is MainContract.MainScreenState.SystemStatus -> uiStateValue.state.status
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { /*TODO*/ },
            modifier = Modifier.align(Alignment.CenterHorizontally),
            enabled = false
        ) {
            Text(text = "Request update")
        }
    }
}