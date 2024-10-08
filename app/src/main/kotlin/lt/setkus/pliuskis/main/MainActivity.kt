package lt.setkus.pliuskis.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import lt.setkus.pliuskis.main.MainContract.Intent.RequestSystemUpdate
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainActivityViewModel by viewModel()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
    Button(onClick = { /*TODO*/ }) {
        Text(text = "Click!")
    }
}