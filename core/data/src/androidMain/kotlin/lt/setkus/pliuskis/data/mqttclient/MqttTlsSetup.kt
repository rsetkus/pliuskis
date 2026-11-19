package lt.setkus.pliuskis.data.mqttclient

import javax.net.ssl.KeyManagerFactory

/** Gives the key material for [TlsMode.CLIENT_CERTIFICATE]. The app module declares it in release builds only. */
fun interface MqttTlsSetup {
    fun keyManagerFactory(): KeyManagerFactory
}
