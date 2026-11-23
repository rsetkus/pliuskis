package lt.setkus.pliuskis.core.presentation.signin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import lt.setkus.pliuskis.core.auth.AuthProvider
import lt.setkus.pliuskis.core.auth.SignInCancelledException
import lt.setkus.pliuskis.core.presentation.session.AccountDeletionState
import lt.setkus.pliuskis.core.presentation.session.SessionState

/**
 * STORY-5 (kmp_cognito_login, PLIUS-42): deleting an account signed in with Apple also revokes the user's Apple tokens,
 * in the shared `SessionViewModel` over fake auth, token-store, Apple and revoke ports.
 * Covers AC90 (deletion order), AC91 (revoke failures) and AC92 (Google users). The plain DeleteUser flow is STORY-1's
 * `Story1SessionViewModelTest` (AC22).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Story5AppleAccountDeletionTest : FunSpec({

    // AC90
    // GIVEN SignedIn with provider APPLE
    // WHEN the user asks to delete the account and confirms
    // THEN nothing is called before the confirmation; then the Apple port is asked for a fresh code once, the code is
    //   sent to the revoke port once with the current access token, Cognito DeleteUser follows once, the stored session
    //   is removed and the state is SignedOut, so the app shows the sign-in screen
    test("testStory5_appleDeletionRevokesFreshAppleCodeBeforeDeleteUserAndSignsOut") {
        runAccountDeletionTest(AuthProvider.APPLE) { harness ->
            val target = harness.sessionViewModel()
            record(target.state)
            record(target.accountDeletion)
            testScheduler.advanceUntilIdle()
            target.state.value shouldBe SessionState.SignedIn

            target.requestAccountDeletion()
            testScheduler.advanceUntilIdle()
            harness.calls shouldBe emptyList()

            target.confirmAccountDeletion()
            testScheduler.advanceUntilIdle()

            harness.calls shouldContainExactly listOf(
                DeletionCall.APPLE_AUTHORIZATION_CODE,
                DeletionCall.APPLE_REVOKE,
                DeletionCall.COGNITO_DELETE_USER,
            )
            harness.appleTokenRevoker.requests shouldContainExactly listOf(
                AppleRevokeRequest(APPLE_AUTHORIZATION_CODE, sessionTokens(AuthProvider.APPLE, REFRESHED).accessToken),
            )
            harness.authenticator.deleteUserAccessTokens shouldContainExactly
                listOf(sessionTokens(AuthProvider.APPLE, REFRESHED).accessToken)
            harness.tokenStore.stored shouldBe null
            target.state.value shouldBe SessionState.SignedOut
        }
    }

    // AC90
    // GIVEN SignedIn with provider APPLE and the revoke port has not answered yet
    // WHEN the user confirms 'Delete account'
    // THEN DeleteUser is not called and the user is still signed in until the revoke port succeeds; then DeleteUser is
    //   called once and the state is SignedOut
    test("testStory5_appleDeletionCallsDeleteUserOnlyAfterRevokeSucceeds") {
        val revokeGate = CompletableDeferred<Unit>()
        runAccountDeletionTest(AuthProvider.APPLE, onAppleRevoke = { revokeGate.await() }) { harness ->
            val target = harness.sessionViewModel()
            record(target.state)
            record(target.accountDeletion)
            testScheduler.advanceUntilIdle()

            target.requestAccountDeletion()
            target.confirmAccountDeletion()
            testScheduler.advanceUntilIdle()

            harness.appleTokenRevoker.requests.size shouldBe 1
            harness.authenticator.deleteUserAccessTokens shouldBe emptyList()
            harness.tokenStore.stored shouldBe sessionTokens(AuthProvider.APPLE, REFRESHED)
            target.state.value shouldBe SessionState.SignedIn

            revokeGate.complete(Unit)
            testScheduler.advanceUntilIdle()

            harness.authenticator.deleteUserAccessTokens shouldContainExactly
                listOf(sessionTokens(AuthProvider.APPLE, REFRESHED).accessToken)
            harness.tokenStore.stored shouldBe null
            target.state.value shouldBe SessionState.SignedOut
        }
    }

    // AC91
    // GIVEN SignedIn with provider APPLE
    // WHEN the user confirms 'Delete account' and then cancels the Apple re-confirmation
    // THEN nothing is called after the Apple port, the stored session is kept, the user stays signed in and the
    //   deletion state shows no error (Idle, not DeleteFailed)
    test("testStory5_cancelledAppleReconfirmationCallsNothingElseAndStaysSignedInWithoutError") {
        runAccountDeletionTest(
            AuthProvider.APPLE,
            onAuthorizationCode = { throw SignInCancelledException() },
        ) { harness ->
            val target = harness.sessionViewModel()
            val deletionStates = record(target.accountDeletion)
            record(target.state)
            testScheduler.advanceUntilIdle()

            target.requestAccountDeletion()
            target.confirmAccountDeletion()
            testScheduler.advanceUntilIdle()

            harness.calls shouldContainExactly listOf(DeletionCall.APPLE_AUTHORIZATION_CODE)
            harness.appleTokenRevoker.requests shouldBe emptyList()
            harness.authenticator.deleteUserAccessTokens shouldBe emptyList()
            harness.authenticator.revokedRefreshTokens shouldBe emptyList()
            harness.tokenStore.stored shouldBe sessionTokens(AuthProvider.APPLE, REFRESHED)
            target.state.value shouldBe SessionState.SignedIn
            target.accountDeletion.value shouldBe AccountDeletionState.Idle
            deletionStates.contains(AccountDeletionState.DeleteFailed) shouldBe false
        }
    }

    // AC91
    // GIVEN SignedIn with provider APPLE and the revoke port fails
    // WHEN the user confirms 'Delete account'
    // THEN DeleteUser is not called, the stored session is kept, the user stays signed in and the deletion state is
    //   DeleteFailed
    test("testStory5_failedAppleRevokeSkipsDeleteUserAndShowsDeleteFailed") {
        runAccountDeletionTest(
            AuthProvider.APPLE,
            onAppleRevoke = { throw IllegalStateException("revoke failed") },
        ) { harness ->
            val target = harness.sessionViewModel()
            record(target.state)
            record(target.accountDeletion)
            testScheduler.advanceUntilIdle()

            target.requestAccountDeletion()
            target.confirmAccountDeletion()
            testScheduler.advanceUntilIdle()

            harness.calls shouldContainExactly listOf(DeletionCall.APPLE_AUTHORIZATION_CODE, DeletionCall.APPLE_REVOKE)
            harness.authenticator.deleteUserAccessTokens shouldBe emptyList()
            harness.tokenStore.stored shouldBe sessionTokens(AuthProvider.APPLE, REFRESHED)
            target.state.value shouldBe SessionState.SignedIn
            target.accountDeletion.value shouldBe AccountDeletionState.DeleteFailed
        }
    }

    // AC92
    // GIVEN SignedIn with provider GOOGLE
    // WHEN the user asks to delete the account and confirms
    // THEN no Apple or revoke call is made, Cognito DeleteUser is called once with the current access token, the stored
    //   session is removed and the state is SignedOut, as in STORY-1 AC22
    test("testStory5_googleDeletionMakesNoAppleCallAndDeletesUserOnce") {
        runAccountDeletionTest(AuthProvider.GOOGLE) { harness ->
            val target = harness.sessionViewModel()
            record(target.state)
            record(target.accountDeletion)
            testScheduler.advanceUntilIdle()
            target.state.value shouldBe SessionState.SignedIn

            target.requestAccountDeletion()
            target.confirmAccountDeletion()
            testScheduler.advanceUntilIdle()

            harness.calls shouldContainExactly listOf(DeletionCall.COGNITO_DELETE_USER)
            harness.appleTokenRevoker.requests shouldBe emptyList()
            harness.authenticator.deleteUserAccessTokens shouldContainExactly
                listOf(sessionTokens(AuthProvider.GOOGLE, REFRESHED).accessToken)
            harness.tokenStore.stored shouldBe null
            target.state.value shouldBe SessionState.SignedOut
        }
    }
})
