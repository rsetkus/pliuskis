package lt.setkus.pliuskis.core.presentation.signin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import lt.setkus.pliuskis.core.auth.AuthTokens
import lt.setkus.pliuskis.core.auth.SessionRejectedException
import lt.setkus.pliuskis.core.presentation.session.AccountDeletionState
import lt.setkus.pliuskis.core.presentation.session.SessionState

/**
 * STORY-1 (kmp_cognito_login, PLIUS-34): the session at launch, sign-out and account deletion, in the shared
 * `SessionViewModel` over fake auth and token-store ports.
 * Covers AC18 (session at launch), AC21 (sign-out) and AC22 (account deletion). The screens that follow the session
 * state are checked on Android by `Story1SignInNavigationTest` (app) and on iOS by `Story1SignInUITests`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Story1SessionViewModelTest : FunSpec({

    // AC18
    // GIVEN no stored session
    // WHEN the session ViewModel checks the session at launch
    // THEN the session state is SignedOut, so the app shows the sign-in screen
    test("testStory1_noStoredSessionIsSignedOut") {
        runSignInTest { harness ->
            val target = harness.sessionViewModel()
            val states = record(target.state)

            testScheduler.advanceUntilIdle()

            target.state.value shouldBe SessionState.SignedOut
            states.last() shouldBe SessionState.SignedOut
        }
    }

    // AC18
    // GIVEN a stored session whose refresh succeeds
    // WHEN the session ViewModel checks the session at launch
    // THEN the state goes from Checking to SignedIn and is never SignedOut, so the sign-in screen is never shown
    test("testStory1_storedSessionWithSuccessfulRefreshIsSignedInWithoutShowingSignIn") {
        runSignInTest(tokenStore = InMemoryTokenStore(STORED_TOKENS)) { harness ->
            val target = harness.sessionViewModel()
            val states = record(target.state)

            testScheduler.advanceUntilIdle()

            target.state.value shouldBe SessionState.SignedIn
            states.distinct() shouldContainExactly listOf(SessionState.Checking, SessionState.SignedIn)
            states shouldNotContain SessionState.SignedOut
            harness.authenticator.refreshRequests shouldContainExactly listOf(STORED_TOKENS.refreshToken)
        }
    }

    // AC18
    // GIVEN a stored session whose refresh is rejected (expired or revoked refresh token)
    // WHEN the session ViewModel checks the session at launch
    // THEN the state is SignedOut and the stored session is removed
    test("testStory1_storedSessionWithRejectedRefreshIsSignedOutAndRemoved") {
        val authenticator = FakeAuthenticator(onRefresh = { throw SessionRejectedException() })
        runSignInTest(authenticator, InMemoryTokenStore(STORED_TOKENS)) { harness ->
            val target = harness.sessionViewModel()
            record(target.state)

            testScheduler.advanceUntilIdle()

            target.state.value shouldBe SessionState.SignedOut
            harness.tokenStore.stored shouldBe null
        }
    }

    // AC18
    // GIVEN a stored session whose refresh has not answered yet
    // WHEN the session state is read while the stored session is being checked
    // THEN the state is Checking (neither the sign-in screen nor the device list)
    test("testStory1_stateIsCheckingWhileStoredSessionIsBeingChecked") {
        val refreshGate = CompletableDeferred<AuthTokens>()
        val authenticator = FakeAuthenticator(onRefresh = { refreshGate.await() })
        runSignInTest(authenticator, InMemoryTokenStore(STORED_TOKENS)) { harness ->
            val target = harness.sessionViewModel()
            val states = record(target.state)

            testScheduler.advanceUntilIdle()

            target.state.value shouldBe SessionState.Checking
            states.distinct() shouldContainExactly listOf(SessionState.Checking)

            refreshGate.complete(REFRESHED_TOKENS)
            testScheduler.advanceUntilIdle()

            target.state.value shouldBe SessionState.SignedIn
        }
    }

    // AC21
    // GIVEN SignedIn
    // WHEN the user signs out
    // THEN the refresh token is revoked with Cognito once, the stored session is removed and the state is SignedOut
    test("testStory1_signOutRevokesRefreshTokenOnceAndRemovesStoredSession") {
        runSignInTest(tokenStore = InMemoryTokenStore(STORED_TOKENS)) { harness ->
            val target = harness.sessionViewModel()
            record(target.state)
            testScheduler.advanceUntilIdle()
            target.state.value shouldBe SessionState.SignedIn

            target.signOut()
            testScheduler.advanceUntilIdle()

            harness.authenticator.revokedRefreshTokens shouldContainExactly listOf(STORED_TOKENS.refreshToken)
            harness.tokenStore.stored shouldBe null
            target.state.value shouldBe SessionState.SignedOut
        }
    }

    // AC21
    // GIVEN SignedIn and Cognito fails the revoke call
    // WHEN the user signs out
    // THEN the stored session is still removed and the state is SignedOut
    test("testStory1_signOutRemovesStoredSessionEvenWhenRevokeFails") {
        val authenticator = FakeAuthenticator(onRevoke = { throw IllegalStateException("revoke failed") })
        runSignInTest(authenticator, InMemoryTokenStore(STORED_TOKENS)) { harness ->
            val target = harness.sessionViewModel()
            record(target.state)
            testScheduler.advanceUntilIdle()
            target.state.value shouldBe SessionState.SignedIn

            target.signOut()
            testScheduler.advanceUntilIdle()

            harness.authenticator.revokedRefreshTokens shouldContainExactly listOf(STORED_TOKENS.refreshToken)
            harness.tokenStore.stored shouldBe null
            target.state.value shouldBe SessionState.SignedOut
        }
    }

    // AC22
    // GIVEN SignedIn
    // WHEN the user asks to delete the account and confirms
    // THEN Cognito DeleteUser is called once with the current access token, the stored session is removed and the
    //   state is SignedOut, so the app shows the sign-in screen
    test("testStory1_confirmedAccountDeletionDeletesUserOnceAndSignsOut") {
        runSignInTest(tokenStore = InMemoryTokenStore(STORED_TOKENS)) { harness ->
            val target = harness.sessionViewModel()
            record(target.state)
            record(target.accountDeletion)
            testScheduler.advanceUntilIdle()
            target.state.value shouldBe SessionState.SignedIn

            target.requestAccountDeletion()
            testScheduler.advanceUntilIdle()
            target.accountDeletion.value shouldBe AccountDeletionState.Confirming
            harness.authenticator.deleteUserAccessTokens shouldBe emptyList()

            target.confirmAccountDeletion()
            testScheduler.advanceUntilIdle()

            harness.authenticator.deleteUserAccessTokens shouldContainExactly listOf(REFRESHED_TOKENS.accessToken)
            harness.tokenStore.stored shouldBe null
            target.state.value shouldBe SessionState.SignedOut
        }
    }

    // AC22
    // GIVEN SignedIn and the delete confirmation is shown
    // WHEN the user cancels the confirmation
    // THEN nothing is called, the user stays signed in and the stored session is kept
    test("testStory1_cancelledAccountDeletionCallsNothing") {
        runSignInTest(tokenStore = InMemoryTokenStore(STORED_TOKENS)) { harness ->
            val target = harness.sessionViewModel()
            record(target.state)
            record(target.accountDeletion)
            testScheduler.advanceUntilIdle()

            target.requestAccountDeletion()
            testScheduler.advanceUntilIdle()
            target.cancelAccountDeletion()
            testScheduler.advanceUntilIdle()

            harness.authenticator.deleteUserAccessTokens shouldBe emptyList()
            harness.authenticator.revokedRefreshTokens shouldBe emptyList()
            target.accountDeletion.value shouldBe AccountDeletionState.Idle
            target.state.value shouldBe SessionState.SignedIn
            harness.tokenStore.stored shouldNotBe null
        }
    }

    // AC22
    // GIVEN SignedIn and Cognito DeleteUser fails
    // WHEN the user confirms the account deletion
    // THEN the user stays signed in, the stored session is kept and the deletion state is DeleteFailed
    test("testStory1_failedAccountDeletionStaysSignedInAndShowsDeleteFailed") {
        val authenticator = FakeAuthenticator(onDeleteUser = { throw IllegalStateException("DeleteUser failed") })
        runSignInTest(authenticator, InMemoryTokenStore(STORED_TOKENS)) { harness ->
            val target = harness.sessionViewModel()
            record(target.state)
            record(target.accountDeletion)
            testScheduler.advanceUntilIdle()

            target.requestAccountDeletion()
            testScheduler.advanceUntilIdle()
            target.confirmAccountDeletion()
            testScheduler.advanceUntilIdle()

            harness.authenticator.deleteUserAccessTokens shouldContainExactly listOf(REFRESHED_TOKENS.accessToken)
            target.state.value shouldBe SessionState.SignedIn
            target.accountDeletion.value shouldBe AccountDeletionState.DeleteFailed
            harness.tokenStore.stored shouldNotBe null
        }
    }
})
