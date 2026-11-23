package lt.setkus.pliuskis.core.auth

/**
 * The Apple re-confirmation of account deletion (the Apple port). With Cognito federation the app never sees the
 * user's Apple tokens, so before an Apple user's account is deleted, the user confirms with Apple again and the app
 * gets a fresh Apple authorization code, which the backend exchanges for a token and revokes ([AppleTokenRevoker]).
 * Android asks Apple in a Custom Tab (the stack's Services ID), iOS with the native Sign in with Apple sheet (the app's
 * App ID).
 */
interface AppleAuthorization {
    /**
     * Asks the user to confirm with Apple and returns the fresh authorization code. Throws [SignInCancelledException]
     * when the user closes the sheet or the browser; any other failure is a plain failure.
     */
    suspend fun authorizationCode(): String
}
