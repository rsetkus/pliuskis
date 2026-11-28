package lt.setkus.pliuskis.data.auth

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import lt.setkus.pliuskis.core.auth.AuthNetworkException
import lt.setkus.pliuskis.core.auth.AuthProvider
import lt.setkus.pliuskis.core.auth.AuthTokens
import lt.setkus.pliuskis.core.auth.Authenticator
import lt.setkus.pliuskis.core.auth.SessionRejectedException

/** What the token request asks for: an ID token (`openid`), the email, and DeleteUser (`...user.admin`). */
internal const val SIGN_IN_SCOPES = "openid email aws.cognito.signin.user.admin"

private const val DELETE_USER_TARGET = "AWSCognitoIdentityProviderService.DeleteUser"
private val AmzJson = ContentType("application", "x-amz-json-1.1")
private val json = Json { ignoreUnknownKeys = true }

/**
 * The [Authenticator] over the Cognito user pool's managed login (OAuth 2.0 authorization code grant with PKCE, a
 * public app client) and the Cognito user pool API, with plain HTTPS: no AWS SDK.
 *
 * - [signIn]: [browser] shows `/oauth2/authorize` with the provider (`Google` or `SignInWithApple`), then the code is
 *   exchanged at `/oauth2/token`.
 * - [refresh]: `/oauth2/token` with the refresh token; Cognito's `invalid_grant` is [SessionRejectedException].
 * - [revoke]: `/oauth2/revoke`.
 * - [deleteUser]: the `DeleteUser` action of `cognito-idp.<region>.amazonaws.com`, authorised by the access token
 *   (it needs the `aws.cognito.signin.user.admin` scope).
 *
 * A request that does not reach Cognito is [AuthNetworkException]. Nothing here logs a token, a code or a URL.
 */
internal class CognitoAuthenticator(
    private val settings: SignInSettings,
    private val browser: AuthorizationBrowser,
    private val http: HttpClient,
) : Authenticator {

    private val tokenUrl get() = "https://${settings.loginDomain}/oauth2/token"
    private val revokeUrl get() = "https://${settings.loginDomain}/oauth2/revoke"
    private val userPoolApiUrl get() = "https://cognito-idp.${settings.region}.amazonaws.com/"

    override suspend fun signIn(provider: AuthProvider): AuthTokens {
        check(settings.isComplete) { "Sign-in is not configured for this build" }
        val pkce = newPkce()
        val state = newOAuthState()
        val redirect = browser.authorize(authorizeUrl(provider, pkce, state), settings.redirectUri)
        val code = authorizationCode(redirect, state)
        return requestTokens(
            form = parameters {
                append("grant_type", "authorization_code")
                append("client_id", settings.appClientId)
                append("code", code)
                append("redirect_uri", settings.redirectUri)
                append("code_verifier", pkce.verifier)
            },
            refreshToken = null,
        )
    }

    override suspend fun refresh(refreshToken: String): AuthTokens = requestTokens(
        form = parameters {
            append("grant_type", "refresh_token")
            append("client_id", settings.appClientId)
            append("refresh_token", refreshToken)
        },
        refreshToken = refreshToken,
    )

    override suspend fun revoke(refreshToken: String) {
        val response = send {
            http.submitForm(
                url = revokeUrl,
                formParameters = parameters {
                    append("token", refreshToken)
                    append("client_id", settings.appClientId)
                },
            )
        }
        check(response.status.isSuccess()) { "Cognito revoke failed with HTTP ${response.status.value}" }
    }

    override suspend fun deleteUser(accessToken: String) {
        val response = send {
            http.post(userPoolApiUrl) {
                header("X-Amz-Target", DELETE_USER_TARGET)
                setBody(TextContent(json.encodeToString(DeleteUserRequest(accessToken)), AmzJson))
            }
        }
        check(response.status.isSuccess()) { "Cognito DeleteUser failed with HTTP ${response.status.value}" }
    }

    /** The managed login page of [provider], with the PKCE challenge and [state]. */
    internal fun authorizeUrl(provider: AuthProvider, pkce: Pkce, state: String): String =
        URLBuilder("https://${settings.loginDomain}/oauth2/authorize").apply {
            parameters.append("response_type", "code")
            parameters.append("client_id", settings.appClientId)
            parameters.append("redirect_uri", settings.redirectUri)
            parameters.append("identity_provider", provider.cognitoName)
            parameters.append("scope", SIGN_IN_SCOPES)
            parameters.append("state", state)
            parameters.append("code_challenge", pkce.challenge)
            parameters.append("code_challenge_method", "S256")
        }.buildString()

    /** The authorization code of the [redirect], after checking that it answers the sign-in that sent [state]. */
    private fun authorizationCode(redirect: String, state: String): String {
        val query = Url(redirect).parameters
        val error = query["error"]
        check(error == null) { "The managed login returned the error $error" }
        check(query["state"] == state) { "The managed login answered another sign-in" }
        return checkNotNull(query["code"]) { "The managed login returned no authorization code" }
    }

    private suspend fun requestTokens(form: io.ktor.http.Parameters, refreshToken: String?): AuthTokens {
        val response = send { http.submitForm(url = tokenUrl, formParameters = form) }
        val body = response.bodyAsText()
        return when {
            response.status.isSuccess() -> json.decodeFromString<TokenResponse>(body).toAuthTokens(refreshToken)
            refreshToken != null && errorCode(body) == INVALID_GRANT -> throw SessionRejectedException()
            else -> error("Cognito token request failed with HTTP ${response.status.value} (${errorCode(body)})")
        }
    }

    /** Sends a request; when it can't reach Cognito, the failure is [AuthNetworkException]. */
    private suspend fun send(request: suspend () -> HttpResponse): HttpResponse =
        try {
            request()
        } catch (error: IOException) {
            throw AuthNetworkException(error)
        }

    private fun errorCode(body: String): String? =
        Either.catch { json.decodeFromString<TokenError>(body).error }.getOrNull()

    private companion object {
        const val INVALID_GRANT = "invalid_grant"
    }
}

/** The identity provider names of the user pool for the managed login's `identity_provider` parameter. */
internal val AuthProvider.cognitoName: String
    get() = when (this) {
        AuthProvider.GOOGLE -> "Google"
        AuthProvider.APPLE -> "SignInWithApple"
    }

/** The token endpoint's answer. A refresh answer has no refresh token: the stored one stays valid. */
@Serializable
private class TokenResponse(
    @SerialName("id_token") val idToken: String,
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
) {
    fun toAuthTokens(previousRefreshToken: String?): AuthTokens = AuthTokens(
        idToken = idToken,
        accessToken = accessToken,
        refreshToken = checkNotNull(refreshToken ?: previousRefreshToken) { "Cognito returned no refresh token" },
    )
}

@Serializable
private class TokenError(val error: String? = null)

@Serializable
private class DeleteUserRequest(@SerialName("AccessToken") val accessToken: String)
