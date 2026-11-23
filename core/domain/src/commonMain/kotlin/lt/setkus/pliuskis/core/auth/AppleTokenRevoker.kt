package lt.setkus.pliuskis.core.auth

/**
 * Revokes a user's Apple tokens with Apple (the revoke port). Apple's revoke call needs a client secret signed with
 * the Sign in with Apple private key, which never ships in an app, so the backend's revoke endpoint makes it: it
 * exchanges the code for a token and revokes that token. The endpoint accepts only a valid Cognito access token of its
 * own stack.
 */
interface AppleTokenRevoker {
    /**
     * Revokes the Apple tokens behind [authorizationCode] (from [AppleAuthorization]), authorised by the signed-in
     * user's Cognito [accessToken]. Returns when Apple has revoked them; any failure is thrown.
     */
    suspend fun revoke(authorizationCode: String, accessToken: String)
}
