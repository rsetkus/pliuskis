package lt.setkus.pliuskis.core.presentation.signin

import lt.setkus.pliuskis.core.auth.AuthProvider

/** The sign-in screen's state. It holds meanings, not display text; each platform maps [Error.reason] to its text. */
sealed interface SignInState {
    data object SignedOut : SignInState
    data class SigningIn(val provider: AuthProvider) : SignInState
    data object SignedIn : SignInState
    data class Error(val reason: SignInError) : SignInState
}

/** Why a sign-in failed. */
enum class SignInError {
    /** No network connection reached Cognito. */
    NETWORK,

    /** Any other failure. */
    FAILED,
}
