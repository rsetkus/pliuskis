package lt.setkus.pliuskis.core.auth

/**
 * The Cognito user pool tokens of one signed-in user. [toString] never prints a token, so the tokens can't reach a log
 * by accident.
 */
data class AuthTokens(
    val idToken: String,
    val accessToken: String,
    val refreshToken: String,
) {
    override fun toString(): String = "AuthTokens(<redacted>)"
}
