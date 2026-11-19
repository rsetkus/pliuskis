package lt.setkus.pliuskis.broker

import android.content.res.AssetManager
import lt.setkus.pliuskis.data.mqttclient.MqttTlsSetup
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory

private const val KEYSTORE_ASSET = "legacy-keystore.p12"
private const val KEYSTORE_TYPE = "PKCS12"

/**
 * Loads the shared AWS IoT client certificate from the gitignored asset `app/src/main/assets/legacy-keystore.p12`.
 * The asset is only read when the MQTT client is created, so a debug build never needs it.
 */
class AssetKeystoreTlsSetup(
    private val assets: AssetManager,
    private val password: String,
) : MqttTlsSetup {

    override fun keyManagerFactory(): KeyManagerFactory {
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
        assets.open(KEYSTORE_ASSET).buffered().use { stream -> keyStore.load(stream, password.toCharArray()) }
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, password.toCharArray())
        return keyManagerFactory
    }
}
