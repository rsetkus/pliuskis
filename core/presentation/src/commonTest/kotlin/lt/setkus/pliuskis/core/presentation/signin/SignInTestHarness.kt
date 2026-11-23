// The harness class and its helpers share one file, named for the role the story's file list gives it.
@file:Suppress("MatchingDeclarationName")

package lt.setkus.pliuskis.core.presentation.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import lt.setkus.pliuskis.core.auth.DeleteAccountUseCase
import lt.setkus.pliuskis.core.auth.ObserveSessionUseCase
import lt.setkus.pliuskis.core.auth.RestoreSessionUseCase
import lt.setkus.pliuskis.core.auth.SignInUseCase
import lt.setkus.pliuskis.core.auth.SignOutUseCase
import lt.setkus.pliuskis.core.presentation.session.SessionViewModel
import kotlin.reflect.KClass

/**
 * Builds the sign-in and session ViewModels over one [authenticator] and one [tokenStore], as the app's Koin graph
 * does, and keeps them in a [ViewModelStore] so each test clears them before Dispatchers.Main is reset.
 *
 * This is the only place the STORY-1 tests construct the ViewModels and use cases: if the Developer splits the use
 * cases differently, change the wiring here, not the assertions.
 */
internal class SignInHarness(
    val authenticator: FakeAuthenticator,
    val tokenStore: InMemoryTokenStore,
) {
    private val store = ViewModelStore()
    private var created = 0

    fun sessionViewModel(): SessionViewModel = keep(SessionViewModel::class) {
        SessionViewModel(
            restoreSession = RestoreSessionUseCase(authenticator, tokenStore),
            observeSession = ObserveSessionUseCase(tokenStore),
            signOut = SignOutUseCase(authenticator, tokenStore),
            deleteAccount = DeleteAccountUseCase(authenticator, tokenStore),
        )
    }

    fun signInViewModel(): SignInViewModel = keep(SignInViewModel::class) {
        SignInViewModel(signIn = SignInUseCase(authenticator, tokenStore))
    }

    private fun <VM : ViewModel> keep(viewModelClass: KClass<VM>, create: () -> VM): VM {
        created += 1
        return ViewModelProvider.create(
            store,
            viewModelFactory { addInitializer(viewModelClass) { create() } },
        )["signInHarness$created", viewModelClass]
    }

    fun clear() = store.clear()
}

/**
 * Runs [block] with a test Main dispatcher and a [SignInHarness] over [authenticator] and [tokenStore]. The harness is
 * cleared before Dispatchers.Main is reset.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun runSignInTest(
    authenticator: FakeAuthenticator = FakeAuthenticator(),
    tokenStore: InMemoryTokenStore = InMemoryTokenStore(),
    block: suspend TestScope.(SignInHarness) -> Unit,
) {
    val dispatcher = StandardTestDispatcher()
    Dispatchers.setMain(dispatcher)
    try {
        runTest(dispatcher) {
            val harness = SignInHarness(authenticator, tokenStore)
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

/**
 * Collects [flow] for the rest of the test and returns every value it showed, in order. The collector is unconfined,
 * so it sees each new value as it is set, and it keeps a `WhileSubscribed` state flow running.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> TestScope.record(flow: StateFlow<T>): List<T> {
    val seen = mutableListOf<T>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        flow.collect { seen += it }
    }
    return seen
}
