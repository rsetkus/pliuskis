package lt.setkus.pliuskis.data.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import kotlinx.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import lt.setkus.pliuskis.core.auth.AppleTokenRevoker
import lt.setkus.pliuskis.core.auth.AuthNetworkException

private val json = Json { ignoreUnknownKeys = true }

/**
 * The [AppleTokenRevoker] over the backend's revoke endpoint (`POST <api>/apple/revoke`, a Lambda behind an API
 * Gateway JWT authorizer): the request carries the Cognito access token as `Authorization: Bearer`, and its JSON body
 * the fresh Apple authorization code and the [client] it belongs to. The Lambda exchanges the code with Apple and
 * revokes the token; a 2xx answer means Apple revoked it. A request that does not reach the API is
 * [AuthNetworkException]. Nothing here logs the code, the token or the request.
 */
internal class LambdaAppleTokenRevoker(
    private val settings: AppleRevokeSettings,
    private val client: AppleRevokeClient,
    private val http: HttpClient,
) : AppleTokenRevoker {

    override suspend fun revoke(authorizationCode: String, accessToken: String) {
        check(settings.hasApi) { "The Apple revoke API is not configured for this build" }
        val response = send {
            http.post(settings.revokeUrl) {
                bearerAuth(accessToken)
                setBody(
                    TextContent(
                        json.encodeToString(RevokeRequest(authorizationCode, client.wireName)),
                        ContentType.Application.Json,
                    )
                )
            }
        }
        check(response.status.isSuccess()) { "The Apple revoke failed with HTTP ${response.status.value}" }
    }

    /** Sends a request; when it can't reach the API, the failure is [AuthNetworkException]. */
    private suspend fun send(request: suspend () -> HttpResponse): HttpResponse =
        try {
            request()
        } catch (error: IOException) {
            throw AuthNetworkException(error)
        }
}

@Serializable
private class RevokeRequest(
    @SerialName("authorizationCode") val authorizationCode: String,
    @SerialName("client") val client: String,
)
