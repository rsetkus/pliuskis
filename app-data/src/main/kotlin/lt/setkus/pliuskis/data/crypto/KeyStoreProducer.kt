package lt.setkus.pliuskis.data.crypto

import java.security.KeyStore

interface KeyStoreProducer {
    fun getKeyStore(): KeyStore
}