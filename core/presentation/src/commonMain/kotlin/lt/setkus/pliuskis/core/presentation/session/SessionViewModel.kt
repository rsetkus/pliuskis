package lt.setkus.pliuskis.core.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lt.setkus.pliuskis.core.auth.DeleteAccountUseCase
import lt.setkus.pliuskis.core.auth.ObserveSessionUseCase
import lt.setkus.pliuskis.core.auth.RestoreSessionUseCase
import lt.setkus.pliuskis.core.auth.SignInCancelledException
import lt.setkus.pliuskis.core.auth.SignOutUseCase
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * Whether someone is signed in, for the whole app: the app shows the sign-in screen for [SessionState.SignedOut] and
 * the device list for [SessionState.SignedIn]. At creation it checks the stored session once ([SessionState.Checking]:
 * a stored refresh token is refreshed with Cognito, a rejected one is removed), then follows the stored session, so a
 * sign-in on the sign-in screen moves it to [SessionState.SignedIn]. It also runs sign-out and the "Delete account"
 * flow ([accountDeletion]).
 *
 * Other modules create it with [newSessionViewModel]: the constructor takes use cases, which stay out of the Swift API
 * of the `shared` framework (Swift: `createSessionViewModel()`).
 */
class SessionViewModel internal constructor(
    restoreSession: RestoreSessionUseCase,
    observeSession: ObserveSessionUseCase,
    signOut: SignOutUseCase,
    deleteAccount: DeleteAccountUseCase,
) : ViewModel() {

    private val signOutUseCase = signOut
    private val deleteAccountUseCase = deleteAccount

    /**
     * Starts [SessionState.Checking]. The flow never fails: a failure to read the stored session is
     * [SessionState.SignedOut].
     */
    val state: StateFlow<SessionState> = flow {
        restoreSession(Unit).first()
        emitAll(
            observeSession(Unit).map { result ->
                result.fold(
                    ifLeft = { SessionState.SignedOut },
                    ifRight = { signedIn -> if (signedIn) SessionState.SignedIn else SessionState.SignedOut },
                )
            }
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Checking)

    private val _accountDeletion = MutableStateFlow<AccountDeletionState>(AccountDeletionState.Idle)

    /** The "Delete account" flow: Idle, then Confirming, Deleting and, when Cognito fails, DeleteFailed. */
    val accountDeletion: StateFlow<AccountDeletionState> = _accountDeletion.asStateFlow()

    /** Revokes the refresh token and removes the stored session; the state becomes [SessionState.SignedOut]. */
    fun signOut() {
        viewModelScope.launch { signOutUseCase(Unit).first() }
    }

    /** Shows the delete confirmation. Nothing is deleted until [confirmAccountDeletion]. */
    fun requestAccountDeletion() {
        _accountDeletion.update { current ->
            if (current == AccountDeletionState.Deleting) current else AccountDeletionState.Confirming
        }
    }

    /** Closes the confirmation (or the failure message) without deleting anything. */
    fun cancelAccountDeletion() {
        _accountDeletion.update { current ->
            if (current == AccountDeletionState.Deleting) current else AccountDeletionState.Idle
        }
    }

    /**
     * Deletes the account with Cognito DeleteUser, from the confirmation or (to try again) the failure message. An
     * Apple user first confirms with Apple again and the Apple tokens are revoked. On success the stored session is
     * removed and the state becomes [SessionState.SignedOut]; on failure the user stays signed in and
     * [accountDeletion] is [AccountDeletionState.DeleteFailed]. When the user cancels the Apple confirmation, nothing
     * is deleted and [accountDeletion] goes back to [AccountDeletionState.Idle], with no error.
     */
    fun confirmAccountDeletion() {
        val current = _accountDeletion.value
        if (current == AccountDeletionState.Confirming || current == AccountDeletionState.DeleteFailed) {
            _accountDeletion.value = AccountDeletionState.Deleting
            viewModelScope.launch {
                val result = deleteAccountUseCase(Unit).first()
                _accountDeletion.value = result.fold(
                    ifLeft = { error -> deletionStateAfter(error) },
                    ifRight = { AccountDeletionState.Idle },
                )
            }
        }
    }

    /** A cancelled Apple confirmation is not an error: the user just chose not to delete the account. */
    private fun deletionStateAfter(error: Throwable): AccountDeletionState =
        if (error is SignInCancelledException) AccountDeletionState.Idle else AccountDeletionState.DeleteFailed
}

/** A session ViewModel over the session use cases, for the Koin graphs of Android and `shared`. Hidden from Swift. */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
fun newSessionViewModel(
    restoreSession: RestoreSessionUseCase,
    observeSession: ObserveSessionUseCase,
    signOut: SignOutUseCase,
    deleteAccount: DeleteAccountUseCase,
): SessionViewModel = SessionViewModel(restoreSession, observeSession, signOut, deleteAccount)
