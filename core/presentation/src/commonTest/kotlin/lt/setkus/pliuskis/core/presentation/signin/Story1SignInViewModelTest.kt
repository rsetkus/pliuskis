package lt.setkus.pliuskis.core.presentation.signin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import lt.setkus.pliuskis.core.auth.AuthNetworkException
import lt.setkus.pliuskis.core.auth.AuthProvider
import lt.setkus.pliuskis.core.auth.AuthTokens
import lt.setkus.pliuskis.core.auth.SignInCancelledException
import lt.setkus.pliuskis.core.presentation.session.SessionState

/**
 * STORY-1 (kmp_cognito_login, PLIUS-34): signing in with Google or Apple in the shared `SignInViewModel`, over fake
 * auth and token-store ports.
 * Covers AC19 (sign-in) and AC20 (sign-in failures). Every state is compared by value, so a state that held display
 * text instead of a meaning (`SignInError`) would not match.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Story1SignInViewModelTest : FunSpec({

    // AC19
    // GIVEN SignedOut
    // WHEN the user chooses Google and the port has not answered yet
    // THEN the state is SigningIn(GOOGLE) and the auth port was asked once, for GOOGLE
    test("testStory1_choosingGoogleSignsInOnceWithGoogle") {
        val signInGate = CompletableDeferred<AuthTokens>()
        runSignInTest(FakeAuthenticator(onSignIn = { signInGate.await() })) { harness ->
            val target = harness.signInViewModel()
            record(target.state)
            target.state.value shouldBe SignInState.SignedOut

            target.signIn(AuthProvider.GOOGLE)
            testScheduler.advanceUntilIdle()

            target.state.value shouldBe SignInState.SigningIn(AuthProvider.GOOGLE)
            harness.authenticator.signInRequests shouldContainExactly listOf(AuthProvider.GOOGLE)
        }
    }

    // AC19
    // GIVEN SignedOut
    // WHEN the user chooses Apple and the port has not answered yet
    // THEN the state is SigningIn(APPLE) and the auth port was asked once, for APPLE
    test("testStory1_choosingAppleSignsInOnceWithApple") {
        val signInGate = CompletableDeferred<AuthTokens>()
        runSignInTest(FakeAuthenticator(onSignIn = { signInGate.await() })) { harness ->
            val target = harness.signInViewModel()
            record(target.state)

            target.signIn(AuthProvider.APPLE)
            testScheduler.advanceUntilIdle()

            target.state.value shouldBe SignInState.SigningIn(AuthProvider.APPLE)
            harness.authenticator.signInRequests shouldContainExactly listOf(AuthProvider.APPLE)
        }
    }

    // AC19
    // GIVEN SigningIn(GOOGLE)
    // WHEN the user chooses Google again, then Apple
    // THEN the auth port is asked nothing more and the state stays SigningIn(GOOGLE)
    test("testStory1_secondChoiceWhileSigningInAsksNothingMore") {
        val signInGate = CompletableDeferred<AuthTokens>()
        runSignInTest(FakeAuthenticator(onSignIn = { signInGate.await() })) { harness ->
            val target = harness.signInViewModel()
            record(target.state)
            target.signIn(AuthProvider.GOOGLE)
            testScheduler.advanceUntilIdle()

            target.signIn(AuthProvider.GOOGLE)
            target.signIn(AuthProvider.APPLE)
            testScheduler.advanceUntilIdle()

            harness.authenticator.signInRequests shouldContainExactly listOf(AuthProvider.GOOGLE)
            target.state.value shouldBe SignInState.SigningIn(AuthProvider.GOOGLE)
        }
    }

    // AC19
    // GIVEN SignedOut
    // WHEN the user chooses Google and the port returns tokens
    // THEN the tokens are stored and the state becomes SignedIn
    test("testStory1_returnedTokensAreStoredAndStateIsSignedIn") {
        runSignInTest { harness ->
            val target = harness.signInViewModel()
            val states = record(target.state)

            target.signIn(AuthProvider.GOOGLE)
            testScheduler.advanceUntilIdle()

            harness.tokenStore.stored shouldBe SIGNED_IN_TOKENS
            target.state.value shouldBe SignInState.SignedIn
            states.distinct() shouldContainExactly listOf(
                SignInState.SignedOut,
                SignInState.SigningIn(AuthProvider.GOOGLE),
                SignInState.SignedIn,
            )
        }
    }

    // AC19 (the app follows the session)
    // GIVEN the session is SignedOut (no stored session) and the sign-in screen is shown
    // WHEN the user signs in with Apple and the port returns tokens
    // THEN the session state becomes SignedIn, so the app leaves the sign-in screen
    test("testStory1_successfulSignInMovesSessionToSignedIn") {
        runSignInTest { harness ->
            val session = harness.sessionViewModel()
            record(session.state)
            val signIn = harness.signInViewModel()
            record(signIn.state)
            testScheduler.advanceUntilIdle()
            session.state.value shouldBe SessionState.SignedOut

            signIn.signIn(AuthProvider.APPLE)
            testScheduler.advanceUntilIdle()

            session.state.value shouldBe SessionState.SignedIn
        }
    }

    // AC20
    // GIVEN SigningIn
    // WHEN the user cancels the browser or the Apple sheet
    // THEN the state returns to SignedOut with no error, and nothing is stored
    test("testStory1_cancelledSignInReturnsToSignedOutWithoutError") {
        val authenticator = FakeAuthenticator(onSignIn = { throw SignInCancelledException() })
        runSignInTest(authenticator) { harness ->
            val target = harness.signInViewModel()
            val states = record(target.state)

            target.signIn(AuthProvider.APPLE)
            testScheduler.advanceUntilIdle()

            target.state.value shouldBe SignInState.SignedOut
            states.forEach { it.shouldNotBeInstanceOf<SignInState.Error>() }
            harness.tokenStore.stored shouldBe null
        }
    }

    // AC20
    // GIVEN SigningIn
    // WHEN the auth port fails because there is no network
    // THEN the state is Error(NETWORK), and nothing is stored
    test("testStory1_signInWithoutNetworkShowsNetworkError") {
        val authenticator = FakeAuthenticator(onSignIn = { throw AuthNetworkException() })
        runSignInTest(authenticator) { harness ->
            val target = harness.signInViewModel()
            record(target.state)

            target.signIn(AuthProvider.GOOGLE)
            testScheduler.advanceUntilIdle()

            target.state.value shouldBe SignInState.Error(SignInError.NETWORK)
            harness.tokenStore.stored shouldBe null
        }
    }

    // AC20
    // GIVEN SigningIn
    // WHEN the auth port fails for any other reason
    // THEN the state is Error(FAILED), not Error(NETWORK)
    test("testStory1_signInOtherFailureShowsFailedError") {
        val authenticator = FakeAuthenticator(onSignIn = { throw IllegalStateException("token exchange failed") })
        runSignInTest(authenticator) { harness ->
            val target = harness.signInViewModel()
            val states = record(target.state)

            target.signIn(AuthProvider.GOOGLE)
            testScheduler.advanceUntilIdle()

            target.state.value shouldBe SignInState.Error(SignInError.FAILED)
            states shouldNotContain SignInState.Error(SignInError.NETWORK)
        }
    }

    // AC20
    // GIVEN Error(NETWORK) after a failed sign-in
    // WHEN the user chooses a provider again and the port now returns tokens
    // THEN a new sign-in starts (the port is asked again) and the state becomes SignedIn
    test("testStory1_choosingProviderAgainFromErrorStartsNewSignIn") {
        val authenticator = FakeAuthenticator(onSignIn = { throw AuthNetworkException() })
        runSignInTest(authenticator) { harness ->
            val target = harness.signInViewModel()
            record(target.state)
            target.signIn(AuthProvider.GOOGLE)
            testScheduler.advanceUntilIdle()
            target.state.value shouldBe SignInState.Error(SignInError.NETWORK)

            authenticator.onSignIn = { SIGNED_IN_TOKENS }
            target.signIn(AuthProvider.APPLE)
            testScheduler.advanceUntilIdle()

            authenticator.signInRequests shouldContainExactly listOf(AuthProvider.GOOGLE, AuthProvider.APPLE)
            target.state.value shouldBe SignInState.SignedIn
            harness.tokenStore.stored shouldBe SIGNED_IN_TOKENS
        }
    }
})
