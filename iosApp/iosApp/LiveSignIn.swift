import Shared
import SwiftUI

/// The sign-in screen of the running app. It owns the shared `SignInViewModel` through `IosViewModelStoreOwner` and
/// passes its state to `SignInScreen`; the buttons call `signIn(provider:)`. It does not navigate: after a successful
/// sign-in the session (`LiveSession`) shows the device list, which releases this screen and clears its ViewModel.
struct LiveSignIn: View {
    static let signInViewModelKey = "signIn"

    @StateObject private var owner = IosViewModelStoreOwner()
    @State private var state: SignInState?

    var body: some View {
        let signIn: SignInViewModel = owner.viewModel(key: Self.signInViewModelKey) {
            SharedEntryPointKt.createSignInViewModel()
        }
        SignInScreen(
            state: state ?? signIn.state.value,
            onGoogle: { signIn.signIn(provider: .google) },
            onApple: { signIn.signIn(provider: .apple) }
        )
        .task {
            for await latest in signIn.state {
                state = latest
            }
        }
    }
}
