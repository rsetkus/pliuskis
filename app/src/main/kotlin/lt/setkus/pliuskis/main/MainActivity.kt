package lt.setkus.pliuskis.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connected
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos.QOS0
import lt.setkus.pliuskis.BuildConfig
import timber.log.Timber
import java.io.BufferedInputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            show()
        }

        storeKeyStore()

        val manager = AWSIotMqttManager(
            "123",
            BuildConfig.AMAZON_IOT_ENDPOINT
        )

        val keyStore = AWSIotKeystoreHelper.getIotKeystore(
            BuildConfig.CERT_ID,
            filesDir.absolutePath,
            BuildConfig.KEYSTORE_NAME,
            BuildConfig.KEYSTORE_PASSWORD
        )
        manager.connect(keyStore) { status: AWSIotMqttClientStatus, throwable: Throwable? ->
            Timber.d(status.toString())
            if (status == Connected) {
                manager.subscribeToTopic("command", QOS0) { topic: String, payloud: ByteArray ->
                    Timber.d("message:\n%s", payloud.toString(Charsets.UTF_8))
                }
            }
        }
    }

    private fun storeKeyStore() {
        if (!AWSIotKeystoreHelper.isKeystorePresent(filesDir.absolutePath, "iotkeystore")) {
            val cert = readAsset("62b1bdc9beb694244e16a091773bd99c742046739ef2361e69e63ac102459d16-certificate.pem.crt")
            val key = readAsset("62b1bdc9beb694244e16a091773bd99c742046739ef2361e69e63ac102459d16-private.pem.key")
            AWSIotKeystoreHelper.saveCertificateAndPrivateKey(
                BuildConfig.CERT_ID,
                cert,
                key,
                filesDir.absolutePath,
                BuildConfig.KEYSTORE_NAME,
                BuildConfig.KEYSTORE_PASSWORD
            )
        }
    }

    private fun readAsset(name: String) =
        BufferedInputStream(assets.open(name)).use {
            it.readBytes().toString(Charsets.UTF_8)
        }
}

@Preview
@Composable
fun show() {
    Button(onClick = { /*TODO*/ }) {
        Text(text = "Click!")
    }
}