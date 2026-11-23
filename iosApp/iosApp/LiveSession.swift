import Shared
import SwiftUI

/// What the account menu of the device list does: sign out, or start the "Delete account" flow.
struct AccountActions {
    let onSignOut: () -> Void
    let onDeleteAccount: () -> Void
}

/// The running app's root: it follows the shared `SessionViewModel`, which this view owns for the app's lifetime.
/// While the stored session (Keychain) is checked it shows progress; then the sign-in screen (`SignedOut`) or the
/// device navigation (`SignedIn`). Signing out or deleting the account swaps the device navigation for sign-in, which
/// releases the device list and control screens and clears their ViewModels. The delete confirmation and its failure
/// message are alerts on this view.
struct LiveSession: View {
    static let sessionViewModelKey = "session"

    @StateObject private var owner = IosViewModelStoreOwner()
    @State private var state: SessionState?
    @State private var deletion: AccountDeletionState?

    var body: some View {
        let session: SessionViewModel = owner.viewModel(key: Self.sessionViewModelKey) {
            SharedEntryPointKt.createSessionViewModel()
        }
        content(session: session, state: state ?? session.state.value)
            .accountDeletionAlerts(
                state: deletion ?? session.accountDeletion.value,
                onConfirm: { session.confirmAccountDeletion() },
                onCancel: { session.cancelAccountDeletion() }
            )
            .task {
                for await latest in session.state {
                    state = latest
                }
            }
            .task {
                for await latest in session.accountDeletion {
                    deletion = latest
                }
            }
    }

    @ViewBuilder
    private func content(session: SessionViewModel, state: SessionState) -> some View {
        switch onEnum(of: state) {
        case .checking:
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Flora.canvas)
                .accessibilityIdentifier("session.checking")
        case .signedOut:
            LiveSignIn()
        case .signedIn:
            LiveDevices(
                account: AccountActions(
                    onSignOut: { session.signOut() },
                    onDeleteAccount: { session.requestAccountDeletion() }
                )
            )
        }
    }
}

/// The device list and control screens of the running app, with the account menu in the list's toolbar when
/// `account` is given.
struct LiveDevices: View {
    let account: AccountActions?

    var body: some View {
        DevicesNavigation { selection in
            LiveDeviceList(selection: selection, account: account)
        } control: { route in
            LiveControlScreen(deviceId: route.deviceId, deviceName: route.deviceName)
        }
    }
}

/// The account menu in the device list's toolbar: "Sign out" and "Delete account". Deleting asks first.
struct AccountMenu: View {
    let actions: AccountActions

    var body: some View {
        Menu {
            Button("Sign out", systemImage: "rectangle.portrait.and.arrow.right", action: actions.onSignOut)
                .accessibilityIdentifier("account.signOut")
            Button("Delete account", systemImage: "trash", role: .destructive, action: actions.onDeleteAccount)
                .accessibilityIdentifier("account.delete")
        } label: {
            Label("Account", systemImage: "person.crop.circle")
        }
        .accessibilityIdentifier("account.menu")
    }
}

extension View {
    /// The "Delete account" flow for the session's deletion `state`: the confirmation (Delete or Cancel; nothing is
    /// deleted before Delete), progress while Cognito deletes the user, and a failure alert (Try again or Cancel)
    /// after which the user is still signed in.
    func accountDeletionAlerts(
        state: AccountDeletionState,
        onConfirm: @escaping () -> Void,
        onCancel: @escaping () -> Void
    ) -> some View {
        // The buttons drive the shared state; the alerts close when it leaves their case.
        let confirming = Binding(get: { state is AccountDeletionStateConfirming }, set: { _ in })
        let failed = Binding(get: { state is AccountDeletionStateDeleteFailed }, set: { _ in })
        return alert("Delete your account?", isPresented: confirming) {
            Button("Delete", role: .destructive, action: onConfirm)
            Button("Cancel", role: .cancel, action: onCancel)
        } message: {
            Text("This deletes your Pliuškis account and signs you out. You can't undo it. Your devices keep working.")
        }
        .alert("Couldn't delete your account", isPresented: failed) {
            Button("Try again", action: onConfirm)
            Button("Cancel", role: .cancel, action: onCancel)
        } message: {
            Text("Check your internet connection and try again. You're still signed in.")
        }
        .overlay {
            if state is AccountDeletionStateDeleting {
                ProgressView("Deleting your account…")
                    .padding(Flora.Spacing.lg)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: Flora.Radius.tile))
                    .accessibilityIdentifier("account.deleting")
            }
        }
    }
}
