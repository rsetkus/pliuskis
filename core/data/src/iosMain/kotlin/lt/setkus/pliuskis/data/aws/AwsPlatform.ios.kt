package lt.setkus.pliuskis.data.aws

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.kCCHmacAlgSHA256
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

@OptIn(ExperimentalForeignApi::class, ExperimentalUnsignedTypes::class)
internal actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    // usePinned needs a non-empty array for addressOf(0); an empty key or message is passed as one zero byte of
    // length 0, which CCHmac reads as empty.
    val keyBytes = if (key.isEmpty()) ByteArray(1) else key
    val dataBytes = if (data.isEmpty()) ByteArray(1) else data
    keyBytes.usePinned { keyPin ->
        dataBytes.usePinned { dataPin ->
            digest.usePinned { output ->
                CCHmac(
                    kCCHmacAlgSHA256,
                    keyPin.addressOf(0),
                    key.size.convert(),
                    dataPin.addressOf(0),
                    data.size.convert(),
                    output.addressOf(0),
                )
            }
        }
    }
    return digest.asByteArray()
}

internal actual fun epochSecondsNow(): Long = NSDate().timeIntervalSince1970.toLong()
