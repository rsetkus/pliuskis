package lt.setkus.pliuskis.core.auth

import arrow.core.Either
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private val json = Json { ignoreUnknownKeys = true }

/**
 * The provider the user signed in with, read from the ID token's `identities` claim, which Cognito writes for a
 * federated user (`providerName` `SignInWithApple` or `Google`). Null when the ID token is not a JWT or names no known
 * provider. Only the claim is read: the token came from Cognito over HTTPS and is not verified again here.
 */
internal fun AuthTokens.signInProvider(): AuthProvider? =
    Either.catch { identityProviderNames(idToken).firstNotNullOfOrNull(::authProviderOf) }.getOrNull()

@OptIn(ExperimentalEncodingApi::class)
private fun identityProviderNames(idToken: String): List<String> {
    val payload = idToken.split('.')[1]
    val claims = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(payload).decodeToString()
    return json.decodeFromString<IdTokenClaims>(claims).identities.mapNotNull { identity -> identity.providerName }
}

private fun authProviderOf(providerName: String): AuthProvider? = when (providerName) {
    "SignInWithApple" -> AuthProvider.APPLE
    "Google" -> AuthProvider.GOOGLE
    else -> null
}

@Serializable
private class IdTokenClaims(val identities: List<Identity> = emptyList())

@Serializable
private class Identity(val providerName: String? = null)
