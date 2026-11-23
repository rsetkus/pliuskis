package lt.setkus.pliuskis.data.aws

import lt.setkus.pliuskis.data.auth.sha256
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/** The SigV4 service name of AWS IoT's MQTT gateway. */
internal const val IOT_SIGNING_SERVICE = "iotdevicegateway"

private const val ALGORITHM = "AWS4-HMAC-SHA256"
private const val PATH = "/mqtt"
private const val SIGNED_HEADERS = "host"
private const val TERMINATOR = "aws4_request"
private const val SECONDS_PER_DAY = 86_400L
private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_MINUTE = 60L
private const val BYTE_MASK = 0xFF
private const val HEX_RADIX = 16
private const val HEX_DIGITS_PER_BYTE = 2
private const val YEAR_DIGITS = 4
private const val TWO_DIGITS = 2

/**
 * Where the session token of temporary credentials goes in the signed URL. AWS IoT's own SDKs add it after signing;
 * the general SigV4 rule puts it in the signed query. STORY-4's check against AWS IoT records which form it accepts.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SecurityTokenPlacement", exact = true)
enum class SecurityTokenPlacement {
    /** `X-Amz-Security-Token` is part of the canonical query, so the signature covers it. */
    SIGNED,

    /** `X-Amz-Security-Token` is added after `X-Amz-Signature`, outside the signed query. */
    APPENDED,
}

/**
 * The SigV4-signed (query string) URL `wss://<endpoint>/mqtt?...` of AWS IoT's MQTT over WebSocket, for [credentials]
 * at [signingTimeEpochSeconds]: `X-Amz-Algorithm=AWS4-HMAC-SHA256`, `X-Amz-Credential` scoped to
 * `<date>/<region>/iotdevicegateway/aws4_request`, `X-Amz-Date`, `X-Amz-SignedHeaders=host`, `X-Amz-Signature` and the
 * session token as `X-Amz-Security-Token`, where [tokenPlacement] says. Every query value is percent-encoded
 * (RFC 3986), so the `+`, `/` and `=` of a session token are `%2B`, `%2F` and `%3D`.
 *
 * The URL carries the signature and the session token: never log it.
 */
internal fun signAwsIotWebSocketUrl(
    credentials: AwsCredentials,
    endpoint: String,
    region: String,
    signingTimeEpochSeconds: Long,
    tokenPlacement: SecurityTokenPlacement,
): String {
    val host = endpoint.lowercase()
    val time = SigningTime(signingTimeEpochSeconds)
    val scope = "${time.date}/$region/$IOT_SIGNING_SERVICE/$TERMINATOR"
    val signedQuery = buildList {
        add("X-Amz-Algorithm" to ALGORITHM)
        add("X-Amz-Credential" to "${credentials.accessKeyId}/$scope")
        add("X-Amz-Date" to time.dateTime)
        add("X-Amz-SignedHeaders" to SIGNED_HEADERS)
        if (tokenPlacement == SecurityTokenPlacement.SIGNED) add("X-Amz-Security-Token" to credentials.sessionToken)
    }
    val canonicalQuery = canonicalQuery(signedQuery)
    val canonicalRequest = listOf(
        "GET",
        PATH,
        canonicalQuery,
        "host:$host\n",
        SIGNED_HEADERS,
        sha256(ByteArray(0)).toHex(),
    ).joinToString("\n")
    val stringToSign = listOf(
        ALGORITHM,
        time.dateTime,
        scope,
        sha256(canonicalRequest.encodeToByteArray()).toHex(),
    ).joinToString("\n")
    val signature = hmacSha256(signingKey(credentials.secretAccessKey, time.date, region), stringToSign.utf8()).toHex()
    val appendedToken = if (tokenPlacement == SecurityTokenPlacement.APPENDED) {
        "&X-Amz-Security-Token=${uriEncode(credentials.sessionToken)}"
    } else {
        ""
    }
    return "wss://$host$PATH?$canonicalQuery&X-Amz-Signature=$signature$appendedToken"
}

/** The SigV4 signing key: HMAC chain over the date, region, service and `aws4_request`. */
private fun signingKey(secretAccessKey: String, date: String, region: String): ByteArray =
    listOf(date, region, IOT_SIGNING_SERVICE, TERMINATOR)
        .fold("AWS4$secretAccessKey".utf8()) { key, part -> hmacSha256(key, part.utf8()) }

/** `key=value` pairs, each part percent-encoded, sorted by encoded key, joined with `&`. */
private fun canonicalQuery(parameters: List<Pair<String, String>>): String =
    parameters
        .map { (key, value) -> uriEncode(key) to uriEncode(value) }
        .sortedBy { it.first }
        .joinToString("&") { (key, value) -> "$key=$value" }

/** RFC 3986 percent-encoding of every byte except the unreserved `A-Z a-z 0-9 - _ . ~`, as SigV4 requires. */
private fun uriEncode(value: String): String = buildString {
    value.utf8().forEach { byte ->
        val char = (byte.toInt() and BYTE_MASK).toChar()
        if (char.isUnreserved()) append(char) else append('%').append(byte.toHexByte().uppercase())
    }
}

private fun Char.isUnreserved(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this == '-' || this == '_' || this == '.' || this == '~'

private fun String.utf8(): ByteArray = encodeToByteArray()

private fun ByteArray.toHex(): String = joinToString("") { it.toHexByte() }

private fun Byte.toHexByte(): String = (toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(HEX_DIGITS_PER_BYTE, '0')

/** The UTC date `yyyyMMdd` and date-time `yyyyMMdd'T'HHmmss'Z'` of SigV4, for seconds since the epoch. */
private class SigningTime(epochSeconds: Long) {
    val date: String
    val dateTime: String

    init {
        val days = epochSeconds.floorDiv(SECONDS_PER_DAY)
        val secondOfDay = epochSeconds.mod(SECONDS_PER_DAY)
        val (year, month, day) = civilFromDays(days)
        date = "${year.pad(YEAR_DIGITS)}${month.pad(TWO_DIGITS)}${day.pad(TWO_DIGITS)}"
        val hour = secondOfDay / SECONDS_PER_HOUR
        val minute = secondOfDay % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val second = secondOfDay % SECONDS_PER_MINUTE
        dateTime = "${date}T${hour.pad(TWO_DIGITS)}${minute.pad(TWO_DIGITS)}${second.pad(TWO_DIGITS)}Z"
    }

    private fun Long.pad(width: Int): String = toString().padStart(width, '0')

    /** Year, month and day of the proleptic Gregorian calendar for days since 1970-01-01 (H. Hinnant's algorithm). */
    @Suppress("MagicNumber")
    private fun civilFromDays(daysSinceEpoch: Long): Triple<Long, Long, Long> {
        val z = daysSinceEpoch + 719_468
        val era = (if (z >= 0) z else z - 146_096) / 146_097
        val dayOfEra = z - era * 146_097
        val yearOfEra = (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
        val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
        val monthIndex = (5 * dayOfYear + 2) / 153
        val day = dayOfYear - (153 * monthIndex + 2) / 5 + 1
        val month = if (monthIndex < 10) monthIndex + 3 else monthIndex - 9
        val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0
        return Triple(year, month, day)
    }
}
