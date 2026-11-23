package lt.setkus.pliuskis.core.presentation.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import lt.setkus.pliuskis.core.auth.AuthNetworkException
import lt.setkus.pliuskis.core.auth.AuthProvider
import lt.setkus.pliuskis.core.auth.SignInCancelledException
import lt.setkus.pliuskis.core.auth.SignInUseCase
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * The sign-in screen: [signIn] with Google or Apple through Cognito's managed login. One sign-in runs at a time; a
 * choice while [SignInState.SigningIn] is ignored. A cancelled browser or Apple sheet returns to
 * [SignInState.SignedOut] without an error. After a successful sign-in the tokens are stored, and the session
 * (`SessionViewModel`) moves the app on to the device list.
 *
 * Other modules create it with [newSignInViewModel]: the constructor takes a use case, which stays out of the Swift API
 * of the `shared` framework (Swift: `createSignInViewModel()`).
 */
class SignInViewModel internal constructor(
    signIn: SignInUseCase,
) : ViewModel() {

    private val signInUseCase = signIn

    private val _state = MutableStateFlow<SignInState>(SignInState.SignedOut)

    /** Starts [SignInState.SignedOut]. The flow never fails: every failure is a state. */
    val state: StateFlow<SignInState> = _state.asStateFlow()

    /** Signs in with [provider], unless a sign-in is already running or has succeeded. */
    fun signIn(provider: AuthProvider) {
        val current = _state.value
        if (current !is SignInState.SigningIn && current != SignInState.SignedIn) {
            _state.value = SignInState.SigningIn(provider)
            viewModelScope.launch {
                val result = signInUseCase(provider).first()
                _state.value = result.fold(ifLeft = ::failureState, ifRight = { SignInState.SignedIn })
            }
        }
    }

    private fun failureState(error: Throwable): SignInState = when (error) {
        is SignInCancelledException -> SignInState.SignedOut
        is AuthNetworkException -> SignInState.Error(SignInError.NETWORK)
        else -> SignInState.Error(SignInError.FAILED)
    }
}

/** A sign-in ViewModel over [signIn], for the Koin graphs of Android and `shared`. Hidden from Swift. */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
fun newSignInViewModel(signIn: SignInUseCase): SignInViewModel = SignInViewModel(signIn)
