package lt.setkus.pliuskis.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import arrow.core.Either
import co.touchlab.kermit.Logger
import java.io.File
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ANDROID_KEY_STORE = "AndroidKeyStore"
private const val KEY_ALIAS = "lt.setkus.pliuskis.session"
private const val KEY_SIZE_BITS = 256
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val TAG_LENGTH_BITS = 128
private const val FILE_NAME = "session.bin"

/**
 * Android [SecureSessionStorage]: the session is encrypted with AES-GCM under a key that lives in the Android
 * Keystore (it never leaves it) and written to `session.bin` in the app's no-backup directory, so it is neither in
 * SharedPreferences nor in a backup. The file holds the IV length, the IV and the ciphertext. If it can't be decrypted
 * (the key was lost, say after a restore to another phone), it is removed and the user signs in again.
 */
internal class KeystoreSessionStorage(context: Context) : SecureSessionStorage {

    private val file = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))

    override fun read(): String? =
        if (file.baseFile.exists()) {
            Either.catch { decrypt(file.readFully()) }
                .onLeft {
                    Logger.withTag(TAG).w { "The stored session can't be decrypted; removing it" }
                    file.delete()
                }
                .getOrNull()
        } else {
            null
        }

    override fun write(session: String) {
        val encrypted = encrypt(session)
        val stream = file.startWrite()
        try {
            stream.write(encrypted)
            file.finishWrite(stream)
        } catch (error: IOException) {
            file.failWrite(stream)
            throw error
        }
    }

    override fun delete() {
        file.delete()
    }

    private fun encrypt(session: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(session.encodeToByteArray())
    }

    private fun decrypt(stored: ByteArray): String {
        val ivLength = stored.first().toInt()
        val iv = stored.copyOfRange(1, 1 + ivLength)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(stored.copyOfRange(1 + ivLength, stored.size)).decodeToString()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: newKey()
    }

    private fun newKey(): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .build()
            )
        }.generateKey()

    private companion object {
        const val TAG = "TokenStore"
    }
}
