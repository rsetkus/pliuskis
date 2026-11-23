package lt.setkus.pliuskis.data.auth

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A PKCE pair (RFC 7636) for one sign-in: the managed login gets [challenge], the token request [verifier], so an
 * authorization code another app intercepts can't be exchanged for tokens.
 */
internal class Pkce(val verifier: String, val challenge: String)

/**
 * A new PKCE pair. The verifier is two random UUIDs as hex (64 characters); `Uuid.random()` uses the platform's secure
 * random generator. The challenge is the S256 method: base64url(SHA-256(verifier)) without padding.
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalEncodingApi::class)
internal fun newPkce(): Pkce {
    val verifier = Uuid.random().toHexString() + Uuid.random().toHexString()
    val challenge = Base64.UrlSafe
        .withPadding(Base64.PaddingOption.ABSENT)
        .encode(sha256(verifier.encodeToByteArray()))
    return Pkce(verifier, challenge)
}

/** A random value that ties the browser's redirect to the sign-in that opened it (the OAuth `state`). */
@OptIn(ExperimentalUuidApi::class)
internal fun newOAuthState(): String = Uuid.random().toHexString()
