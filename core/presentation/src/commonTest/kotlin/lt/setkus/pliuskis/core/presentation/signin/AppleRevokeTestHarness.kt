package lt.setkus.pliuskis.core.presentation.signin

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import lt.setkus.pliuskis.core.auth.AppleAuthorization
import lt.setkus.pliuskis.core.auth.AppleTokenRevoker
import lt.setkus.pliuskis.core.auth.AuthProvider
import lt.setkus.pliuskis.core.auth.AuthTokens
import lt.setkus.pliuskis.core.auth.DeleteAccountUseCase
import lt.setkus.pliuskis.core.auth.ObserveSessionUseCase
import lt.setkus.pliuskis.core.auth.RestoreSessionUseCase
import lt.setkus.pliuskis.core.auth.SignOutUseCase
import lt.setkus.pliuskis.core.presentation.session.SessionViewModel
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** A test value only; no real Apple authorization code. */
internal const val APPLE_AUTHORIZATION_CODE = "fresh-apple-authorization-code"

/** The calls that leave the phone during an account deletion, in the order they arrive. */
internal enum class DeletionCall {
    APPLE_AUTHORIZATION_CODE,
    APPLE_REVOKE,
    COGNITO_DELETE_USER,
    COGNITO_REVOKE,
}

/** One call to the revoke port: the fresh Apple code and the Cognito access token that authorises the call. */
internal data class AppleRevokeRequest(val authorizationCode: String, val accessToken: String)

/** The Apple port: asks the user to confirm with Apple again and returns a fresh authorization code. */
internal class FakeAppleAuthorization(
    private val calls: MutableList<DeletionCall>,
    private val onAuthorizationCode: suspend () -> String,
) : AppleAuthorization {
    override suspend fun authorizationCode(): String {
        calls += DeletionCall.APPLE_AUTHORIZATION_CODE
        return onAuthorizationCode()
    }
}

/** The revoke port: the backend's revoke Lambda, which exchanges the code with Apple and revokes the token. */
internal class FakeAppleTokenRevoker(
    private val calls: MutableList<DeletionCall>,
    private val onRevoke: suspend (AppleRevokeRequest) -> Unit,
) : AppleTokenRevoker {
    val requests = mutableListOf<AppleRevokeRequest>()

    override suspend fun revoke(authorizationCode: String, accessToken: String) {
        val request = AppleRevokeRequest(authorizationCode, accessToken)
        calls += DeletionCall.APPLE_REVOKE
        requests += request
        onRevoke(request)
    }
}

/**
 * The session tokens of a user who signed in with [provider]; [generation] names the token set ("stored" at launch,
 * "refreshed" after the launch refresh). The ID token has the shape of a Cognito ID token of a federated user: its
 * `identities` claim names the provider (`SignInWithApple` or `Google`), as Cognito writes it. Test values only; the
 * signature is not real.
 *
 * This is the only place the STORY-5 tests say how the session knows its provider: if the Developer keeps the provider
 * some other way (stored next to the tokens, say), change this function, not the assertions.
 */
internal fun sessionTokens(provider: AuthProvider, generation: String): AuthTokens =
    AuthTokens(
        idToken = cognitoIdToken(provider),
        accessToken = "$generation-access-token",
        refreshToken = "stored-refresh-token",
    )

@OptIn(ExperimentalEncodingApi::class)
private fun cognitoIdToken(provider: AuthProvider): String {
    val providerName = when (provider) {
        AuthProvider.GOOGLE -> "Google"
        AuthProvider.APPLE -> "SignInWithApple"
    }
    val header = """{"alg":"RS256","kid":"test-key"}"""
    val payload = """{"sub":"test-user","cognito:username":"${providerName.lowercase()}_test-user",""" +
        """"identities":[{"userId":"test-user","providerName":"$providerName","providerType":"$providerName",""" +
        """"primary":"true"}],"token_use":"id"}"""
    val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    return listOf(header, payload).joinToString(".") { base64Url.encode(it.encodeToByteArray()) } + ".test-signature"
}

internal const val STORED = "stored"
internal const val REFRESHED = "refreshed"

/**
 * The session ViewModel of a user signed in with [provider], over fake auth, token-store, Apple and revoke ports.
 * Every call that leaves the phone is recorded in [calls], in order. The stored session is [STORED]; the launch
 * refresh returns [REFRESHED] tokens of the same provider.
 *
 * This is the only place the STORY-5 tests construct the use cases and the ViewModel: if the Developer wires the
 * Apple and revoke ports differently, change the wiring here, not the assertions.
 */
internal class AccountDeletionHarness(
    val provider: AuthProvider,
    onAuthorizationCode: suspend () -> String,
    onAppleRevoke: suspend (AppleRevokeRequest) -> Unit,
    onDeleteUser: suspend (String) -> Unit,
) {
    val calls = mutableListOf<DeletionCall>()
    val tokenStore = InMemoryTokenStore(sessionTokens(provider, STORED))
    val authenticator = FakeAuthenticator(
        onRefresh = { sessionTokens(provider, REFRESHED) },
        onRevoke = { calls += DeletionCall.COGNITO_REVOKE },
        onDeleteUser = { accessToken ->
            calls += DeletionCall.COGNITO_DELETE_USER
            onDeleteUser(accessToken)
        },
    )
    val appleAuthorization = FakeAppleAuthorization(calls, onAuthorizationCode)
    val appleTokenRevoker = FakeAppleTokenRevoker(calls, onAppleRevoke)

    private val store = ViewModelStore()

    fun sessionViewModel(): SessionViewModel = ViewModelProvider.create(
        store,
        viewModelFactory {
            addInitializer(SessionViewModel::class) {
                SessionViewModel(
                    restoreSession = RestoreSessionUseCase(authenticator, tokenStore),
                    observeSession = ObserveSessionUseCase(tokenStore),
                    signOut = SignOutUseCase(authenticator, tokenStore),
                    deleteAccount = DeleteAccountUseCase(
                        authenticator = authenticator,
                        tokenStore = tokenStore,
                        appleAuthorization = appleAuthorization,
                        appleTokenRevoker = appleTokenRevoker,
                    ),
                )
            }
        },
    )[SessionViewModel::class]

    fun clear() = store.clear()
}

/**
 * Runs [block] with a test Main dispatcher and an [AccountDeletionHarness] for [provider]. By default the Apple port
 * returns [APPLE_AUTHORIZATION_CODE], and the revoke port and Cognito DeleteUser succeed. The harness is cleared before
 * Dispatchers.Main is reset.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun runAccountDeletionTest(
    provider: AuthProvider,
    onAuthorizationCode: suspend () -> String = { APPLE_AUTHORIZATION_CODE },
    onAppleRevoke: suspend (AppleRevokeRequest) -> Unit = {},
    onDeleteUser: suspend (String) -> Unit = {},
    block: suspend TestScope.(AccountDeletionHarness) -> Unit,
) {
    val dispatcher = StandardTestDispatcher()
    Dispatchers.setMain(dispatcher)
    try {
        runTest(dispatcher) {
            val harness = AccountDeletionHarness(provider, onAuthorizationCode, onAppleRevoke, onDeleteUser)
            try {
                block(harness)
            } finally {
                harness.clear()
            }
        }
    } finally {
        Dispatchers.resetMain()
    }
}
