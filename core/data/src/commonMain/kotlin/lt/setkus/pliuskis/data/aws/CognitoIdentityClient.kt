package lt.setkus.pliuskis.data.aws

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import kotlinx.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import lt.setkus.pliuskis.core.auth.AuthNetworkException
import lt.setkus.pliuskis.core.auth.SessionRejectedException

private const val GET_ID_TARGET = "AWSCognitoIdentityService.GetId"
private const val GET_CREDENTIALS_TARGET = "AWSCognitoIdentityService.GetCredentialsForIdentity"
private const val NOT_AUTHORIZED = "NotAuthorizedException"
private val AmzJson = ContentType("application", "x-amz-json-1.1")
private val json = Json { ignoreUnknownKeys = true }

/**
 * The Cognito identity pool API (`cognito-identity.<region>.amazonaws.com`) for a signed-in user pool user, with plain
 * HTTPS: no AWS SDK. Both calls are authorised by the user's ID token, given as the login of the user pool
 * [userPoolId] (`cognito-idp.<region>.amazonaws.com/<userPoolId>`).
 *
 * A request that does not reach Cognito is [AuthNetworkException]; a refused ID token (`NotAuthorizedException`, for
 * example an expired one) is [SessionRejectedException]. Nothing here logs a token or a credential.
 */
internal class CognitoIdentityClient(
    private val settings: AwsIotSettings,
    private val userPoolId: String,
    private val http: HttpClient,
) {
    private val apiUrl get() = "https://cognito-identity.${settings.region}.amazonaws.com/"
    private val loginProvider get() = "cognito-idp.${userPoolId.substringBefore('_')}.amazonaws.com/$userPoolId"

    /** The identity ID of the user whose [idToken] this is (`GetId`): `<region>:<uuid>`, the same on every call. */
    suspend fun identityId(idToken: String): String {
        val body = call(GET_ID_TARGET, json.encodeToString(GetIdRequest(settings.identityPoolId, logins(idToken))))
        return json.decodeFromString<GetIdResponse>(body).identityId
    }

    /** New temporary credentials of [identityId] (`GetCredentialsForIdentity`). */
    suspend fun credentials(identityId: String, idToken: String): AwsCredentials {
        val body = call(GET_CREDENTIALS_TARGET, json.encodeToString(GetCredentialsRequest(identityId, logins(idToken))))
        return json.decodeFromString<GetCredentialsResponse>(body).credentials.toAwsCredentials()
    }

    private fun logins(idToken: String): Map<String, String> = mapOf(loginProvider to idToken)

    /** Posts [requestJson] to the [target] action and returns the answer's body, or throws for a refusal. */
    private suspend fun call(target: String, requestJson: String): String {
        val response = send {
            http.post(apiUrl) {
                header("X-Amz-Target", target)
                setBody(TextContent(requestJson, AmzJson))
            }
        }
        val body = response.bodyAsText()
        val errorType = if (response.status.isSuccess()) null else errorType(body)
        return when {
            response.status.isSuccess() -> body
            errorType == NOT_AUTHORIZED -> throw SessionRejectedException()
            else -> error("Cognito identity request failed with HTTP ${response.status.value} ($errorType)")
        }
    }

    /** Sends a request; when it can't reach Cognito, the failure is [AuthNetworkException]. */
    private suspend fun send(request: suspend () -> HttpResponse): HttpResponse =
        try {
            request()
        } catch (error: IOException) {
            throw AuthNetworkException(error)
        }

    /** The short error name of an AWS JSON error body: `NotAuthorizedException` of `...#NotAuthorizedException`. */
    private fun errorType(body: String): String? =
        Either.catch { json.decodeFromString<AwsError>(body).type }.getOrNull()?.substringAfterLast('#')
}

@Serializable
private class GetIdRequest(
    @SerialName("IdentityPoolId") val identityPoolId: String,
    @SerialName("Logins") val logins: Map<String, String>,
)

@Serializable
private class GetIdResponse(@SerialName("IdentityId") val identityId: String)

@Serializable
private class GetCredentialsRequest(
    @SerialName("IdentityId") val identityId: String,
    @SerialName("Logins") val logins: Map<String, String>,
)

@Serializable
private class GetCredentialsResponse(@SerialName("Credentials") val credentials: CredentialsJson)

/** `Expiration` is seconds since the epoch, as a JSON number that may have a fraction. */
@Serializable
private class CredentialsJson(
    @SerialName("AccessKeyId") val accessKeyId: String,
    @SerialName("SecretKey") val secretKey: String,
    @SerialName("SessionToken") val sessionToken: String,
    @SerialName("Expiration") val expiration: Double,
) {
    fun toAwsCredentials(): AwsCredentials = AwsCredentials(
        accessKeyId = accessKeyId,
        secretAccessKey = secretKey,
        sessionToken = sessionToken,
        expiresAtEpochSeconds = expiration.toLong(),
    )
}

@Serializable
private class AwsError(@SerialName("__type") val type: String? = null)
