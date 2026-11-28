import AuthenticationServices
import Shared
import SwiftUI

/// The first screen of a signed-out app, drawn from the state the shared `SignInViewModel` produces: "Continue with
/// Google" and Apple's standard "Sign in with Apple" button. While a sign-in runs (`SigningIn`) both are disabled and
/// a progress indicator shows; after a failure (`Error`) an error line says what happened, and either button starts a
/// new sign-in. Both buttons only call back: the managed login opens in Kotlin (`ASWebAuthenticationSession`). It holds
/// no state of its own.
struct SignInScreen: View {
    let state: SignInState
    let onGoogle: () -> Void
    let onApple: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let busy = state is SignInStateSigningIn || state is SignInStateSignedIn
        VStack(spacing: Flora.Spacing.lg) {
            Spacer()
            Image(systemName: "leaf.fill")
                .font(.system(size: 44, weight: .semibold))
                .foregroundStyle(Flora.onLeafContainer)
                .frame(width: 88, height: 88)
                .background(Flora.leafContainer, in: Circle())
                .accessibilityHidden(true)
            VStack(spacing: Flora.Spacing.xs) {
                Text("Welcome to Pliuškis")
                    .font(.title.bold())
                    .accessibilityAddTraits(.isHeader)
                Text("Sign in to see how your plants are doing and water them from anywhere.")
                    .font(.body)
                    .foregroundStyle(.secondary)
            }
            .multilineTextAlignment(.center)
            Spacer()
            VStack(spacing: Flora.Spacing.sm) {
                Button(action: onGoogle) {
                    Text("Continue with Google")
                        .font(.body.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.bordered)
                .buttonBorderShape(.roundedRectangle(radius: 8))
                .tint(.primary)
                .disabled(busy)
                .accessibilityIdentifier("signIn.google")
                AppleSignInButton(isEnabled: !busy, dark: colorScheme == .dark, action: onApple)
                    .frame(height: 50)
                if busy {
                    ProgressView("Signing you in…")
                        .accessibilityIdentifier("signIn.progress")
                }
                if let error = state as? SignInStateError {
                    Text(Self.message(for: error.reason))
                        .font(.subheadline)
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)
                        .accessibilityIdentifier("signIn.error")
                }
            }
            .frame(maxWidth: 420)
        }
        .padding(.horizontal, Flora.Spacing.lg)
        .padding(.vertical, Flora.Spacing.md)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Flora.canvas)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("signIn.screen")
    }

    /// The error line for a failed sign-in. The state holds the meaning; this is the iOS text.
    static func message(for reason: SignInError) -> String {
        switch reason {
        case .network:
            String(localized: "You're offline. Check your internet connection and try again.")
        case .failed:
            String(localized: "Couldn't sign you in. Please try again.")
        }
    }
}

/// Apple's standard "Sign in with Apple" button (`ASAuthorizationAppleIDButton`), black in light mode and white in
/// dark mode, drawn as the label of a SwiftUI button that calls `action`. SwiftUI's `SignInWithAppleButton` would start
/// the native Apple ID flow, but the app signs in with Apple through the Cognito managed login. The SwiftUI button is
/// the accessibility element (identifier `signIn.apple`), so `disabled` reaches VoiceOver and the UI tests; Apple's view
/// only draws.
struct AppleSignInButton: View {
    let isEnabled: Bool
    let dark: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            AppleIDButtonArtwork(dark: dark)
                // Apple's style is fixed at creation, so a new appearance makes a new view.
                .id(dark)
                .opacity(isEnabled ? 1 : 0.4)
                // Apple's view ignores touches; the whole frame is the SwiftUI button's.
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .accessibilityLabel("Sign in with Apple")
        .accessibilityIdentifier("signIn.apple")
    }
}

/// `ASAuthorizationAppleIDButton` as a picture: no touch handling, hidden from accessibility.
private struct AppleIDButtonArtwork: UIViewRepresentable {
    let dark: Bool

    func makeUIView(context: Context) -> ASAuthorizationAppleIDButton {
        let button = ASAuthorizationAppleIDButton(type: .signIn, style: dark ? .white : .black)
        button.cornerRadius = 8
        button.isUserInteractionEnabled = false
        button.isAccessibilityElement = false
        button.accessibilityElementsHidden = true
        return button
    }

    func updateUIView(_ button: ASAuthorizationAppleIDButton, context: Context) {}
}
