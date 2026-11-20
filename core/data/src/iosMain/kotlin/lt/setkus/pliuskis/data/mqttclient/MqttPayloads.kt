package lt.setkus.pliuskis.data.mqttclient

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

/** The UTF-8 bytes of this string, as the [MqttTransport] sends them. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun String.toUtf8NSData(): NSData {
    val bytes = encodeToByteArray()
    val data = if (bytes.isEmpty()) {
        NSData()
    } else {
        bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    }
    return data
}

/** These payload bytes decoded as UTF-8; invalid sequences become U+FFFD. */
@OptIn(ExperimentalForeignApi::class)
internal fun NSData.decodeUtf8(): String {
    val bytes = ByteArray(length.toInt())
    if (bytes.isNotEmpty()) {
        bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    }
    return bytes.decodeToString()
}
