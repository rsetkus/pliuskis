package lt.setkus.pliuskis.feature.signin

import lt.setkus.pliuskis.core.presentation.session.newSessionViewModel
import lt.setkus.pliuskis.core.presentation.signin.newSignInViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The sign-in and session ViewModels of the Android app. The session ViewModel is the app's (the activity owns it);
 * the sign-in ViewModel belongs to the sign-in destination.
 */
val signInModule = module {
    viewModel { newSignInViewModel(signIn = get()) }
    viewModel {
        newSessionViewModel(
            restoreSession = get(),
            observeSession = get(),
            signOut = get(),
            deleteAccount = get(),
        )
    }
}
