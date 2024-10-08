package lt.setkus.pliuskis.data.mqttclient

import android.content.Context
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttClientSslConfig
import lt.setkus.pliuskis.data.BuildConfig
import java.io.BufferedInputStream
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory

fun getDebugMqttClient() = MqttClient.builder()
    .identifier("android-debug-client")
    .serverHost("10.0.2.2")
    .serverPort(1883)
    .useMqttVersion3()
    .buildRx()

fun getProductionMqttClient(context: Context) = MqttClient.builder()
    .identifier("android-production-client")
    .serverHost(BuildConfig.AMAZON_IOT_ENDPOINT)
    .useMqttVersion3()
    .sslConfig(sslConfig(context))
    .buildRx()

private fun sslConfig(context: Context) = MqttClientSslConfig.builder()
    .keyManagerFactory(getKeyManagerFactory(context))
    .build()

private fun getKeyManagerFactory(context: Context): KeyManagerFactory {
    val keyStore = createKeyStore(context)
    val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    keyManagerFactory.init(keyStore, BuildConfig.KEYSTORE_PASSWORD.toCharArray())
    return keyManagerFactory
}

private fun createKeyStore(context: Context): KeyStore {
    val stream = BufferedInputStream(context.assets.open("legacy-keystore.p12"))
    val keyStore = KeyStore.getInstance("PKCS12")
    keyStore.load(stream, BuildConfig.KEYSTORE_PASSWORD.toCharArray())
    stream.close()
    return keyStore
}