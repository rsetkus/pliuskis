package lt.setkus.pliuskis.data.aws

/** The HMAC-SHA256 of [data] with [key], for the SigV4 signing key and signature. */
internal expect fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray

/** The wall clock: seconds since 1970-01-01T00:00:00Z. SigV4 signs with it, and AWS rejects a far-off clock. */
internal expect fun epochSecondsNow(): Long
