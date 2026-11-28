package lt.setkus.pliuskis.core.presentation.signin

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import lt.setkus.pliuskis.core.auth.AuthProvider
import lt.setkus.pliuskis.core.auth.AuthTokens
import lt.setkus.pliuskis.core.auth.Authenticator
import lt.setkus.pliuskis.core.auth.TokenStore

/** Test values only; no real token. */
internal val STORED_TOKENS = AuthTokens(
    idToken = "stored-id-token",
    accessToken = "stored-access-token",
    refreshToken = "stored-refresh-token",
)

/** What a successful refresh of [STORED_TOKENS] returns: new ID and access tokens, the same refresh token. */
internal val REFRESHED_TOKENS = AuthTokens(
    idToken = "refreshed-id-token",
    accessToken = "refreshed-access-token",
    refreshToken = "stored-refresh-token",
)

/** What a successful sign-in returns. */
internal val SIGNED_IN_TOKENS = AuthTokens(
    idToken = "new-id-token",
    accessToken = "new-access-token",
    refreshToken = "new-refresh-token",
)

/**
 * The auth port as Cognito would answer it. Each call is recorded in the order it arrived (the Cognito calls are this
 * seam's observable output); the `on*` lambdas decide the outcome and may suspend to hold a call open.
 */
internal class FakeAuthenticator(
    var onSignIn: suspend (AuthProvider) -> AuthTokens = { SIGNED_IN_TOKENS },
    var onRefresh: suspend (String) -> AuthTokens = { REFRESHED_TOKENS },
    var onRevoke: suspend (String) -> Unit = {},
    var onDeleteUser: suspend (String) -> Unit = {},
) : Authenticator {
    val signInRequests = mutableListOf<AuthProvider>()
    val refreshRequests = mutableListOf<String>()
    val revokedRefreshTokens = mutableListOf<String>()
    val deleteUserAccessTokens = mutableListOf<String>()

    override suspend fun signIn(provider: AuthProvider): AuthTokens {
        signInRequests += provider
        return onSignIn(provider)
    }

    override suspend fun refresh(refreshToken: String): AuthTokens {
        refreshRequests += refreshToken
        return onRefresh(refreshToken)
    }

    override suspend fun revoke(refreshToken: String) {
        revokedRefreshTokens += refreshToken
        onRevoke(refreshToken)
    }

    override suspend fun deleteUser(accessToken: String) {
        deleteUserAccessTokens += accessToken
        onDeleteUser(accessToken)
    }
}

/** The token-store port in memory; [stored] is what secure storage would hold. */
internal class InMemoryTokenStore(initial: AuthTokens? = null) : TokenStore {
    private val tokens = MutableStateFlow(initial)

    val stored: AuthTokens?
        get() = tokens.value

    override fun observe(): Flow<AuthTokens?> = tokens

    override suspend fun save(tokens: AuthTokens) {
        this.tokens.value = tokens
    }

    override suspend fun clear() {
        tokens.value = null
    }
}
