package lt.setkus.pliuskis

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import lt.setkus.pliuskis.core.auth.AuthProvider
import lt.setkus.pliuskis.core.auth.AuthTokens
import lt.setkus.pliuskis.core.auth.Authenticator
import lt.setkus.pliuskis.core.auth.TokenStore

/** Test values only; no real token. */
internal val APP_TEST_TOKENS = AuthTokens(
    idToken = "app-test-id-token",
    accessToken = "app-test-access-token",
    refreshToken = "app-test-refresh-token",
)

/**
 * A Cognito that answers at once: every sign-in and refresh succeeds with [APP_TEST_TOKENS]. It records the calls, so
 * a test can see that the UI reached the port. It runs inside the app's Koin graph in place of the real
 * authenticator (the fake signed-in session of the UI tests).
 */
internal class AppFakeAuthenticator : Authenticator {
    val signInRequests = mutableListOf<AuthProvider>()
    val revokedRefreshTokens = mutableListOf<String>()

    override suspend fun signIn(provider: AuthProvider): AuthTokens {
        signInRequests += provider
        return APP_TEST_TOKENS
    }

    override suspend fun refresh(refreshToken: String): AuthTokens = APP_TEST_TOKENS

    override suspend fun revoke(refreshToken: String) {
        revokedRefreshTokens += refreshToken
    }

    override suspend fun deleteUser(accessToken: String) = Unit
}

/** The token store in memory, so a test chooses whether a session is stored at launch. */
internal class AppInMemoryTokenStore(initial: AuthTokens?) : TokenStore {
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
