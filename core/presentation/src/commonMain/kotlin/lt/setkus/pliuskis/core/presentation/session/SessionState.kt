package lt.setkus.pliuskis.core.presentation.session

/** Whether someone is signed in. The app shows the sign-in screen for [SignedOut], the device list for [SignedIn]. */
sealed interface SessionState {
    /** The stored session is being checked at launch. */
    data object Checking : SessionState
    data object SignedOut : SessionState
    data object SignedIn : SessionState
}

/** The "Delete account" flow of a signed-in user. */
sealed interface AccountDeletionState {
    data object Idle : AccountDeletionState

    /** The confirmation is shown. */
    data object Confirming : AccountDeletionState

    /** Cognito DeleteUser is running. */
    data object Deleting : AccountDeletionState

    /** DeleteUser failed; the user is still signed in. */
    data object DeleteFailed : AccountDeletionState
}
