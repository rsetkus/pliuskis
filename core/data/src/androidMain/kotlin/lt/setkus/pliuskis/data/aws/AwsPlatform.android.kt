package lt.setkus.pliuskis.data.aws

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val HMAC_SHA256 = "HmacSHA256"
private const val MILLIS_PER_SECOND = 1000L

internal actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val mac = Mac.getInstance(HMAC_SHA256)
    mac.init(SecretKeySpec(key, HMAC_SHA256))
    return mac.doFinal(data)
}

internal actual fun epochSecondsNow(): Long = System.currentTimeMillis() / MILLIS_PER_SECOND
