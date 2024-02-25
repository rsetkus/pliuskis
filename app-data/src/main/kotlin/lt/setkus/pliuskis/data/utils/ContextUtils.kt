package lt.setkus.pliuskis.data.utils

import android.content.Context
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper
import lt.setkus.pliuskis.data.BuildConfig
import java.io.BufferedInputStream
import java.security.KeyStore


private val keystoreProperties: KeystoreProperties = object {
    val properties = KeystoreProperties(
        BuildConfig.KEYSTORE_NAME,
        BuildConfig.KEYSTORE_PASSWORD,
        BuildConfig.CERT_ID,
        BuildConfig.CERT_PEM,
        BuildConfig.KEY_PEM
    )
}.properties

fun Context.readAssetToString(fileName: String) =
    BufferedInputStream(assets.open(fileName)).use {
        it.readBytes().toString(Charsets.UTF_8)
    }

fun Context.getIotKeyStore(): KeyStore {
    if (!AWSIotKeystoreHelper.isKeystorePresent(filesDir.absolutePath, keystoreProperties.name)) {
        storeKeystore(keystoreProperties)
    }
    return retrieveIotKeyStore()
}

private fun Context.retrieveIotKeyStore(): KeyStore = AWSIotKeystoreHelper.getIotKeystore(
    keystoreProperties.certId,
    filesDir.absolutePath,
    keystoreProperties.name,
    keystoreProperties.password
)

private data class KeystoreProperties(
    val name: String,
    val password: String,
    val certId: String,
    val certPem: String,
    val certKeyPem: String,
)

private fun Context.storeKeystore(properties: KeystoreProperties) {
    val cert = readAssetToString(properties.certPem)
    val key = readAssetToString(properties.certKeyPem)
    AWSIotKeystoreHelper.saveCertificateAndPrivateKey(
        properties.certId,
        cert,
        key,
        filesDir.absolutePath,
        properties.name,
        properties.password
    )
}