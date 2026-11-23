package lt.setkus.pliuskis.core.auth

/**
 * Cognito sign-in, as the domain sees it (the auth port). Each platform implements it over the user pool's managed
 * login. A failure is thrown: [SignInCancelledException] when the user closes the browser or the Apple sheet,
 * [AuthNetworkException] when Cognito can't be reached, [SessionRejectedException] when Cognito refuses a refresh
 * token; anything else is a plain failure.
 */
interface Authenticator {
    /** Signs the user in with [provider] and returns the user pool tokens. */
    suspend fun signIn(provider: AuthProvider): AuthTokens

    /** New tokens for a stored session's [refreshToken]. */
    suspend fun refresh(refreshToken: String): AuthTokens

    /** Revokes [refreshToken] with Cognito (sign-out). */
    suspend fun revoke(refreshToken: String)

    /** Deletes the signed-in user with Cognito DeleteUser, authorised by [accessToken]. */
    suspend fun deleteUser(accessToken: String)
}

/** The user closed the browser or the Apple sheet. It is not an error. */
class SignInCancelledException(cause: Throwable? = null) : Exception("Sign-in cancelled", cause)

/** Cognito could not be reached (no network). */
class AuthNetworkException(cause: Throwable? = null) : Exception("Cognito unreachable", cause)

/** Cognito refused the stored refresh token: it expired or was revoked. */
class SessionRejectedException(cause: Throwable? = null) : Exception("Session rejected", cause)
