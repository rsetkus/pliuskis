package lt.setkus.pliuskis.data.auth

/**
 * The backend's Apple revoke API of one stack (`rsetkus/pliuskis-backend`): the dev stack for Debug builds, the prod
 * stack for Release builds. Each app reads the values from its gitignored configuration, like [SignInSettings].
 *
 * @property apiUrl the API's base URL, `https://<api id>.execute-api.<region>.amazonaws.com` (the stack's
 *   `apple_revoke_api_url` output). The revoke endpoint is `<apiUrl>/apple/revoke`; Apple's return URL of the Android
 *   flow is `<apiUrl>/apple/return`.
 * @property servicesId the stack's Sign in with Apple Services ID, the client of Android's browser flow. iOS asks
 *   Apple with the app's own App ID instead and leaves it empty.
 */
class AppleRevokeSettings(
    val apiUrl: String,
    val servicesId: String = "",
) {
    /** The revoke endpoint, which takes the fresh code and a Cognito access token. */
    internal val revokeUrl: String
        get() = "${apiUrl.trimEnd('/')}/apple/revoke"

    /** Where Apple posts the Android flow's code (`response_mode=form_post`); the API sends the browser to the app. */
    internal val returnUrl: String
        get() = "${apiUrl.trimEnd('/')}/apple/return"

    /** False when the API URL is missing or not HTTPS, for example in a build without a configured stack. */
    internal val hasApi: Boolean
        get() = apiUrl.startsWith("https://") && apiUrl.length > "https://".length
}
