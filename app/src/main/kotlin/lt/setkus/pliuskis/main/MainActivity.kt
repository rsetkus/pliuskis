package lt.setkus.pliuskis.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            show()
        }

//        val manager = AWSIotMqttManager(
//            "123",
//            BuildConfig.AMAZON_IOT_ENDPOINT
//        )
//
//        manager.connect(keyStore) { status: AWSIotMqttClientStatus, throwable: Throwable? ->
//            Timber.d(status.toString())
//            if (status == Connected) {
//                manager.subscribeToTopic("command", QOS0) { topic: String, payloud: ByteArray ->
//                    Timber.d("message:\n%s", payloud.toString(Charsets.UTF_8))
//                }
//            }
//        }
    }
}

@Preview
@Composable
fun show() {
    Button(onClick = { /*TODO*/ }) {
        Text(text = "Click!")
    }
}