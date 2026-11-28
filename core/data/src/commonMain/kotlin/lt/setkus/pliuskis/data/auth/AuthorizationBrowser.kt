package lt.setkus.pliuskis.data.auth

/**
 * The system browser that shows Cognito's managed login (Custom Tabs on Android, `ASWebAuthenticationSession` on iOS).
 * It never sees a token: the managed login sends back only an authorization code, which [CognitoAuthenticator]
 * exchanges for tokens.
 */
interface AuthorizationBrowser {
    /**
     * Opens [url] and returns the URL the managed login redirected to, which starts with [redirectUri]. Throws
     * [lt.setkus.pliuskis.core.auth.SignInCancelledException] when the user closes the browser or the Apple sheet.
     */
    suspend fun authorize(url: String, redirectUri: String): String
}
