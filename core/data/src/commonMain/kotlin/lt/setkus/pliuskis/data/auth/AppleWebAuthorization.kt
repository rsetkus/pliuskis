package lt.setkus.pliuskis.data.auth

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import lt.setkus.pliuskis.core.auth.AppleAuthorization
import lt.setkus.pliuskis.core.auth.SignInCancelledException

/** The host of the app's return URI of the Apple flow, `<redirect URI scheme>://apple-revoke`. */
internal const val APPLE_RETURN_HOST = "apple-revoke"

private const val APPLE_AUTHORIZE_URL = "https://appleid.apple.com/auth/authorize"
private const val APPLE_USER_CANCELLED = "user_cancelled_authorize"

/**
 * The [AppleAuthorization] in the system browser (Android's Custom Tab), with the stack's Services ID. Apple's page
 * asks the user to confirm, then posts the code to the backend's return URL (`response_mode=form_post`); the backend
 * sends the browser on to the app's own scheme,
 * `<redirect URI scheme>://apple-revoke?code=...&state=...`, which the browser hands back. The scheme is the sign-in
 * redirect URI's, so the same redirect activity receives it.
 *
 * No scope is asked for: only the code is needed. An OAuth `state` ties the answer to this request. Closing the
 * browser, or Apple's `user_cancelled_authorize`, is [SignInCancelledException].
 */
internal class AppleWebAuthorization(
    private val revokeSettings: AppleRevokeSettings,
    private val signInSettings: SignInSettings,
    private val browser: AuthorizationBrowser,
) : AppleAuthorization {

    override suspend fun authorizationCode(): String {
        check(revokeSettings.hasApi && revokeSettings.servicesId.isNotBlank()) {
            "The Apple revoke API is not configured for this build"
        }
        val state = newOAuthState()
        val redirect = browser.authorize(authorizeUrl(state), appReturnUri())
        return authorizationCode(redirect, state)
    }

    /** Apple's authorization page for the Services ID, returning to the backend's return URL. */
    internal fun authorizeUrl(state: String): String =
        URLBuilder(APPLE_AUTHORIZE_URL).apply {
            parameters.append("client_id", revokeSettings.servicesId)
            parameters.append("redirect_uri", revokeSettings.returnUrl)
            parameters.append("response_type", "code")
            parameters.append("response_mode", "form_post")
            parameters.append("state", state)
        }.buildString()

    private fun appReturnUri(): String = "${signInSettings.redirectUri.substringBefore(':')}://$APPLE_RETURN_HOST"

    /** The code of the [redirect], after checking that it answers the request that sent [state]. */
    private fun authorizationCode(redirect: String, state: String): String {
        val query = Url(redirect).parameters
        val error = query["error"]
        if (error == APPLE_USER_CANCELLED) {
            throw SignInCancelledException()
        }
        check(error == null) { "Apple returned the error $error" }
        check(query["state"] == state) { "Apple answered another request" }
        return checkNotNull(query["code"]) { "Apple returned no authorization code" }
    }
}
