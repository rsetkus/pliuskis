package lt.setkus.pliuskis.data.auth

/** The SHA-256 digest of [bytes], for the PKCE code challenge. */
internal expect fun sha256(bytes: ByteArray): ByteArray
