package lt.setkus.pliuskis.data.crypto

import android.content.Context
import lt.setkus.pliuskis.data.utils.getIotKeyStore
import java.security.KeyStore

class AwsKeyStoreProducer(private val context: Context) : KeyStoreProducer {
    override fun getKeyStore(): KeyStore = context.getIotKeyStore()
}