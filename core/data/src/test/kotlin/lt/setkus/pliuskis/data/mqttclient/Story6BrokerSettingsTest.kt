package lt.setkus.pliuskis.data.mqttclient

import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import lt.setkus.pliuskis.data.di.dataModule
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory

private const val DEBUG_HOST = "10.0.2.2"
private const val DEBUG_PORT = 1883
private const val RELEASE_HOST = "example.iot.amazonaws.com"
private const val RELEASE_PORT = 8883

/** A key manager factory over an empty in-memory PKCS12 store: no keystore file is needed. */
private fun emptyKeyManagerFactory(): KeyManagerFactory {
    val keyStore = KeyStore.getInstance("PKCS12").apply { load(null, null) }
    return KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keyStore, CharArray(0)) }
}

/** Resolves the MQTT client from [dataModule] with the settings (and TLS setup) that `app` would declare. */
private fun clientFrom(appModule: Module): Mqtt3Client {
    val koin = koinApplication { modules(dataModule, appModule) }
    val client = koin.koin.get<Mqtt3Client>()
    koin.close()
    return client
}

/**
 * STORY-6 AC27-AC28: `app` passes [MqttSettings] (and, for TLS, an [MqttTlsSetup]) through Koin, and [dataModule]
 * builds the HiveMQ client from them, with no BuildConfig. Building the client does not connect, so no broker is
 * needed; the test reads the client's configuration.
 */
class Story6BrokerSettingsTest : FunSpec({

    // AC28 (debug)
    // GIVEN debug settings: host 10.0.2.2, port 1883, no TLS, and no TLS setup declared
    // WHEN dataModule creates the MQTT client
    // THEN the client targets 10.0.2.2:1883 without TLS
    test("testStory6_debugSettingsCreateClientForDebugBrokerWithoutTls") {
        val settings = MqttSettings(
            clientId = "abc123",
            host = DEBUG_HOST,
            port = DEBUG_PORT,
            tlsMode = TlsMode.NONE,
        )

        val config = clientFrom(module { single { settings } }).config

        config.serverHost shouldBe DEBUG_HOST
        config.serverPort shouldBe DEBUG_PORT
        config.sslConfig.isPresent.shouldBeFalse()
    }

    // AC28 (release)
    // GIVEN release settings: host example.iot.amazonaws.com, port 8883, client certificate TLS, and a TLS setup
    // WHEN dataModule creates the MQTT client
    // THEN the client targets example.iot.amazonaws.com:8883 with TLS from the TLS setup's key manager factory
    test("testStory6_releaseSettingsCreateClientForEndpointWithKeystoreTls") {
        val keyManagerFactory = emptyKeyManagerFactory()
        val settings = MqttSettings(
            clientId = "abc123",
            host = RELEASE_HOST,
            port = RELEASE_PORT,
            tlsMode = TlsMode.CLIENT_CERTIFICATE,
        )
        val appModule = module {
            single { settings }
            single<MqttTlsSetup> { MqttTlsSetup { keyManagerFactory } }
        }

        val config = clientFrom(appModule).config

        config.serverHost shouldBe RELEASE_HOST
        config.serverPort shouldBe RELEASE_PORT
        config.sslConfig.isPresent.shouldBeTrue()
        config.sslConfig.get().keyManagerFactory.get() shouldBeSameInstanceAs keyManagerFactory
    }

    // AC27
    // GIVEN settings with client ID abc123
    // WHEN dataModule creates the MQTT client
    // THEN the client's identifier is the settings' client ID
    test("testStory6_settingsClientIdIsClientIdentifier") {
        val settings = MqttSettings(
            clientId = "abc123",
            host = DEBUG_HOST,
            port = DEBUG_PORT,
            tlsMode = TlsMode.NONE,
        )

        val config = clientFrom(module { single { settings } }).config

        config.clientIdentifier.get().toString() shouldBe "abc123"
    }
})
