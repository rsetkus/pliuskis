package lt.setkus.pliuskis.data.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

@OptIn(ExperimentalForeignApi::class, ExperimentalUnsignedTypes::class)
internal actual fun sha256(bytes: ByteArray): ByteArray {
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    if (bytes.isNotEmpty()) {
        bytes.usePinned { input ->
            digest.usePinned { output ->
                CC_SHA256(input.addressOf(0), bytes.size.convert(), output.addressOf(0))
            }
        }
    }
    return digest.asByteArray()
}
