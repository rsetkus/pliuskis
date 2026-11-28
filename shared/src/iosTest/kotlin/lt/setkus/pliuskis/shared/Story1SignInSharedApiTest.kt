package lt.setkus.pliuskis.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import lt.setkus.pliuskis.core.auth.AuthProvider
import lt.setkus.pliuskis.core.auth.AuthTokens
import lt.setkus.pliuskis.core.auth.Authenticator
import lt.setkus.pliuskis.core.auth.TokenStore
import lt.setkus.pliuskis.core.presentation.session.SessionState
import lt.setkus.pliuskis.core.presentation.session.SessionViewModel
import lt.setkus.pliuskis.core.presentation.signin.SignInError
import lt.setkus.pliuskis.core.presentation.signin.SignInState
import lt.setkus.pliuskis.core.presentation.signin.SignInViewModel
import lt.setkus.pliuskis.data.mqttclient.MqttSettings
import lt.setkus.pliuskis.data.mqttclient.MqttTransport
import lt.setkus.pliuskis.data.mqttclient.MqttTransportListener
import lt.setkus.pliuskis.data.mqttclient.TlsMode
import org.koin.core.context.loadKoinModules
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import platform.Foundation.NSData
import kotlin.reflect.KClass

private val signInSettings = MqttSettings(
    clientId = "story1signin",
    host = "localhost",
    port = 1883,
    tlsMode = TlsMode.NONE,
)

/** Test values only; no real token. */
private val SHARED_TEST_TOKENS = AuthTokens(
    idToken = "shared-test-id-token",
    accessToken = "shared-test-access-token",
    refreshToken = "shared-test-refresh-token",
)

/** A broker that refuses every connection; sign-in needs no MQTT. */
private class NoBrokerTransport : MqttTransport {
    override var listener: MqttTransportListener? = null

    override fun connect(host: String, port: Int, clientId: String) {
        listener?.onDisconnected()
    }

    override fun disconnect() = Unit

    override fun subscribe(topicFilter: String) = Unit

    override fun unsubscribe(topicFilter: String) = Unit

    override fun publish(topic: String, payload: NSData): Int = -1
}

/** A Cognito whose sign-in is decided by [onSignIn]; it may suspend to hold the sign-in open. */
private class ScriptedAuthenticator(
    private val onSignIn: suspend (AuthProvider) -> AuthTokens,
) : Authenticator {
    override suspend fun signIn(provider: AuthProvider): AuthTokens = onSignIn(provider)

    override suspend fun refresh(refreshToken: String): AuthTokens = SHARED_TEST_TOKENS

    override suspend fun revoke(refreshToken: String) = Unit

    override suspend fun deleteUser(accessToken: String) = Unit
}

/** The token store in memory, empty at start: no one is signed in. */
private class EmptyTokenStore : TokenStore {
    private val tokens = MutableStateFlow<AuthTokens?>(null)

    override fun observe(): Flow<AuthTokens?> = tokens

    override suspend fun save(tokens: AuthTokens) {
        this.tokens.value = tokens
    }

    override suspend fun clear() {
        tokens.value = null
    }
}

/** Keeps the ViewModels a test creates through the factories, so they are cleared before Dispatchers.Main resets. */
private class SignInSharedHarness {
    private val store = ViewModelStore()

    fun <VM : ViewModel> keep(viewModelClass: KClass<VM>, create: () -> VM): VM =
        ViewModelProvider.create(
            store,
            viewModelFactory { addInitializer(viewModelClass) { create() } },
        )[viewModelClass]

    fun clear() = store.clear()
}

/**
 * Starts the graph the way Swift does (`initShared`), then swaps the auth and token-store ports for fakes with a Koin
 * override (the iOS fake signed-in session; Swift itself never sees Koin). Koin is process-wide, so it is stopped
 * again after each test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun runWithSharedSignIn(
    onSignIn: suspend (AuthProvider) -> AuthTokens,
    block: suspend TestScope.(SignInSharedHarness) -> Unit,
) {
    val dispatcher = StandardTestDispatcher()
    Dispatchers.setMain(dispatcher)
    try {
        initShared(signInSettings, NoBrokerTransport())
        loadKoinModules(
            module {
                single<Authenticator> { ScriptedAuthenticator(onSignIn) }
                single<TokenStore> { EmptyTokenStore() }
            }
        )
        runTest(dispatcher) {
            val harness = SignInSharedHarness()
            try {
                block(harness)
            } finally {
                harness.clear()
            }
        }
    } finally {
        stopKoin()
        Dispatchers.resetMain()
    }
}

/**
 * STORY-1 (kmp_cognito_login, PLIUS-34), AC27 (Kotlin side): Swift creates the sign-in and session ViewModels through
 * the `shared` factories `createSignInViewModel()` and `createSessionViewModel()`, and observes the Kotlin states
 * SignedOut, SigningIn, SignedIn and Error. A failing sign-in arrives as an Error state; the state flow never throws
 * (Turbine fails the test if it does). `Story1SignInSwiftTests` (`shared/swift-test`) reads the same states from
 * Swift.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Story1SignInSharedApiTest : FunSpec({

    // AC27
    // GIVEN initShared has started the graph and no session is stored
    // WHEN Swift creates a sign-in ViewModel and signs in with Google, and the sign-in succeeds
    // THEN it observes SignedOut, then SigningIn(GOOGLE), then SignedIn
    test("testStory1_signInViewModelFromFactoryGoesFromSignedOutToSignedIn") {
        val signInGate = CompletableDeferred<AuthTokens>()
        runWithSharedSignIn(onSignIn = { signInGate.await() }) { harness ->
            val target = harness.keep(SignInViewModel::class) { createSignInViewModel() }

            target.state.test {
                awaitItem() shouldBe SignInState.SignedOut
                target.signIn(AuthProvider.GOOGLE)
                awaitItem() shouldBe SignInState.SigningIn(AuthProvider.GOOGLE)
                signInGate.complete(SHARED_TEST_TOKENS)
                awaitItem() shouldBe SignInState.SignedIn
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // AC27
    // GIVEN initShared has started the graph and the sign-in fails
    // WHEN Swift signs in through a sign-in ViewModel from the factory
    // THEN the failure arrives as Error(FAILED), and the flow does not throw
    test("testStory1_failingSignInFromFactoryArrivesAsErrorState") {
        runWithSharedSignIn(onSignIn = { throw IllegalStateException("token exchange failed") }) { harness ->
            val target = harness.keep(SignInViewModel::class) { createSignInViewModel() }

            target.state.test {
                awaitItem() shouldBe SignInState.SignedOut
                target.signIn(AuthProvider.APPLE)
                // The fake fails at once, so the state flow may conflate SigningIn away.
                var state = awaitItem()
                if (state == SignInState.SigningIn(AuthProvider.APPLE)) state = awaitItem()
                state shouldBe SignInState.Error(SignInError.FAILED)
                cancelAndIgnoreRemainingEvents()
            }
            target.state.value shouldBe SignInState.Error(SignInError.FAILED)
        }
    }

    // AC27
    // GIVEN initShared has started the graph and no session is stored
    // WHEN Swift creates a session ViewModel, then signs in through a sign-in ViewModel
    // THEN the session state goes from SignedOut to SignedIn
    test("testStory1_sessionViewModelFromFactoryFollowsSignIn") {
        runWithSharedSignIn(onSignIn = { SHARED_TEST_TOKENS }) { harness ->
            val session = harness.keep(SessionViewModel::class) { createSessionViewModel() }
            val signIn = harness.keep(SignInViewModel::class) { createSignInViewModel() }

            session.state.test {
                var state = awaitItem()
                if (state == SessionState.Checking) state = awaitItem()
                state shouldBe SessionState.SignedOut

                signIn.signIn(AuthProvider.GOOGLE)
                awaitItem() shouldBe SessionState.SignedIn
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
})
