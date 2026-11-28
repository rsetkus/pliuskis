package lt.setkus.pliuskis.data.aws

import arrow.core.Either
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import lt.setkus.pliuskis.core.auth.AuthTokens
import lt.setkus.pliuskis.core.auth.Authenticator
import lt.setkus.pliuskis.core.auth.SessionRejectedException
import lt.setkus.pliuskis.core.auth.TokenStore
import lt.setkus.pliuskis.data.auth.sha256
import kotlin.concurrent.Volatile

/** Credentials that expire sooner than this are refreshed before (or right after) they are used. */
internal const val CREDENTIALS_REFRESH_MARGIN_SECONDS = 5 * 60L

/** Credentials that expire sooner than this are not used to sign a URL any more. */
internal const val CREDENTIALS_SIGNING_MARGIN_SECONDS = 60L

/** How many hex characters of the access key ID's SHA-256 the refresh log shows, to tell two credentials apart. */
private const val KEY_FINGERPRINT_LENGTH = 8
private const val BYTE_MASK = 0xFF
private const val HEX_RADIX = 16

/** No fresh AWS credentials are cached yet, so no URL can be signed now; a refresh has been started. */
class StaleAwsCredentialsException : Exception("No fresh AWS credentials to sign the AWS IoT URL")

/**
 * The signed-in identity's temporary AWS credentials for AWS IoT, and the signed WebSocket URL of every connect
 * attempt.
 *
 * - [prepare] (before a connect attempt that the app asks for) gets the identity ID once, and new credentials when
 *   none are cached or they expire within [CREDENTIALS_REFRESH_MARGIN_SECONDS]. When Cognito refuses the stored ID
 *   token (it lives about an hour), the user pool tokens are refreshed once with the refresh token, so the user never
 *   signs in again for it.
 * - [signedUrlNow] (from the MQTT client's own connect attempts, its automatic reconnects included) signs a new URL at
 *   the current time without waiting for the network: it must not block the socket's thread. With credentials that
 *   expire within the refresh margin it signs and starts a refresh in [scope]; with none, or ones that expire within
 *   [CREDENTIALS_SIGNING_MARGIN_SECONDS], it starts a refresh and throws [StaleAwsCredentialsException], so that
 *   attempt fails and the client's next automatic reconnect signs with the new credentials.
 * - Signing out (the [TokenStore] has no tokens) drops the cached identity and credentials, so no later attempt is
 *   signed for the old identity, and runs the [onSignOut] actions (the MQTT client closes the old identity's
 *   connection).
 * - After AWS IoT refused a connection ([invalidateCredentials]), the next attempt gets new credentials.
 *
 * Nothing here logs a token, a credential or a URL; a refresh logs only the new credentials' expiry and a short
 * fingerprint of their access key ID.
 */
class AwsIotCredentialsCache internal constructor(
    private val settings: AwsIotSettings,
    private val identity: CognitoIdentityClient,
    private val tokenStore: TokenStore,
    private val authenticator: Authenticator,
    private val scope: CoroutineScope,
    private val now: () -> Long = ::epochSecondsNow,
) {
    private class Snapshot(val identityId: String, val credentials: AwsCredentials)

    private val refreshLock = Mutex()

    private val log = Logger.withTag("AwsIotCredentials")

    @Volatile
    private var snapshot: Snapshot? = null

    /** Registered once by each MQTT client when it is created, so a plain copy-on-write list is enough. */
    @Volatile
    private var signOutActions: List<() -> Unit> = emptyList()

    init {
        scope.launch {
            tokenStore.observe().collect { tokens ->
                if (tokens == null) {
                    snapshot = null
                    signOutActions.forEach { it() }
                }
            }
        }
    }

    /**
     * Makes sure fresh credentials are cached and returns the identity ID they belong to, or null when no one is
     * signed in or Cognito gives no credentials (no network, refused session); the connect attempt then fails. No
     * Cognito call is made when no one is signed in.
     */
    suspend fun prepare(): String? = refreshLock.withLock {
        val signedIn = tokenStore.observe().first() != null
        if (!signedIn) snapshot = null
        val current = snapshot?.takeIf { it.credentials.validFor(CREDENTIALS_REFRESH_MARGIN_SECONDS) }
        val stillUsable = { snapshot?.takeIf { it.credentials.validFor(CREDENTIALS_SIGNING_MARGIN_SECONDS) } }
        if (signedIn) (current ?: fetch() ?: stillUsable())?.identityId else null
    }

    /**
     * The client ID of the next AWS IoT connect attempt, `<identity ID>-<install suffix>`, after [prepare] has made
     * the credentials fresh; null when the attempt can't be made: no one is signed in, Cognito gives no credentials, or
     * the stack's settings are incomplete (no endpoint, for example).
     */
    suspend fun clientIdForAttempt(installSuffix: String): String? =
        if (settings.isComplete) prepare()?.let { identityId -> "$identityId-$installSuffix" } else null

    /**
     * AWS IoT refused a connection with the cached credentials (the identity had no AWS IoT policy): forget them, so
     * the next attempt gets new ones from Cognito. The identity ID stays.
     */
    fun invalidateCredentials() {
        snapshot = snapshot?.let { Snapshot(it.identityId, it.credentials.expired()) }
    }

    /** Runs [action] each time the user signs out (the stored session is cleared). */
    fun onSignOut(action: () -> Unit) {
        signOutActions = signOutActions + action
    }

    /** A URL signed now with the cached credentials; see the class description for when it throws. */
    @Throws(StaleAwsCredentialsException::class)
    fun signedUrlNow(): String {
        val current = snapshot?.takeIf { it.credentials.validFor(CREDENTIALS_SIGNING_MARGIN_SECONDS) }
        if (current?.credentials?.validFor(CREDENTIALS_REFRESH_MARGIN_SECONDS) != true) refreshInBackground()
        val signingCredentials = current?.credentials ?: throw StaleAwsCredentialsException()
        return signAwsIotWebSocketUrl(
            credentials = signingCredentials,
            endpoint = settings.endpoint,
            region = settings.region,
            signingTimeEpochSeconds = now(),
            tokenPlacement = settings.tokenPlacement,
        )
    }

    private fun refreshInBackground() {
        if (!refreshLock.isLocked) {
            scope.launch { prepare() }
        }
    }

    /**
     * Must hold [refreshLock]. New credentials for the signed-in user, cached; null when no one is signed in or Cognito
     * gives none. A failed refresh keeps the cached credentials, which may still be valid for a while.
     */
    private suspend fun fetch(): Snapshot? {
        val tokens = tokenStore.observe().first()
        val fetched = tokens?.let { Either.catch { fetchFor(it) }.getOrNull() }
        snapshot = if (tokens == null) null else fetched ?: snapshot
        fetched?.let { log.logRefreshed(it.credentials) }
        return fetched
    }

    private suspend fun fetchFor(tokens: AuthTokens): Snapshot =
        try {
            fetchWith(tokens.idToken)
        } catch (@Suppress("SwallowedException") rejected: SessionRejectedException) {
            // An expired ID token: refresh the user pool tokens once and ask again with the new ID token.
            val refreshed = authenticator.refresh(tokens.refreshToken)
            tokenStore.save(refreshed)
            fetchWith(refreshed.idToken)
        }

    private suspend fun fetchWith(idToken: String): Snapshot {
        val identityId = snapshot?.identityId ?: identity.identityId(idToken)
        return Snapshot(identityId, identity.credentials(identityId, idToken))
    }

    private fun AwsCredentials.validFor(seconds: Long): Boolean = expiresAtEpochSeconds - now() > seconds
}

/** The same credentials, marked as expired, so they are never used again. */
private fun AwsCredentials.expired(): AwsCredentials =
    AwsCredentials(accessKeyId, secretAccessKey, sessionToken, expiresAtEpochSeconds = 0)

/**
 * Logs that new credentials are cached: their expiry and a short fingerprint (the first hex characters of the access
 * key ID's SHA-256), so a check can tell two credentials apart. No part of the access key ID, the secret key or the
 * session token is logged.
 */
private fun Logger.logRefreshed(credentials: AwsCredentials) {
    val fingerprint = sha256(credentials.accessKeyId.encodeToByteArray())
        .joinToString("") { byte -> (byte.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0') }
        .take(KEY_FINGERPRINT_LENGTH)
    i { "AWS IoT credentials refreshed: key #$fingerprint, expire at ${credentials.expiresAtEpochSeconds}" }
}
