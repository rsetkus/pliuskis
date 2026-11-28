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
    // usePinned needs a non-empty array for addressOf(0); an empty input is passed as one zero byte of length 0, so
    // the result is the SHA-256 of the empty message (SigV4 hashes an empty request payload), not 32 zero bytes.
    val input = if (bytes.isEmpty()) ByteArray(1) else bytes
    input.usePinned { pinned ->
        digest.usePinned { output ->
            CC_SHA256(pinned.addressOf(0), bytes.size.convert(), output.addressOf(0))
        }
    }
    return digest.asByteArray()
}
