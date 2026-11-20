import SwiftUI

/// Decides when the visible screen refreshes from SwiftUI's `scenePhase`: once each time the app comes back to
/// `.active` after it was in `.background` (directly or through `.inactive`, as after Device > Lock). The first launch
/// does not refresh (the screen loads its data itself), nor does a short `.inactive` trip such as Control Center or an
/// alert, which never left the foreground. The refresh is the shared ViewModel's `refresh()`: it connects only if
/// needed and asks for the screen's data again. There is no MQTT in the background.
struct ForegroundRefresh {
    private let refresh: () -> Void
    private var wasInBackground = false

    init(refresh: @escaping () -> Void) {
        self.refresh = refresh
    }

    mutating func scenePhaseChanged(to phase: ScenePhase) {
        switch phase {
        case .background:
            wasInBackground = true
        case .active where wasInBackground:
            wasInBackground = false
            refresh()
        default:
            break
        }
    }
}

extension View {
    /// Runs `refresh` each time the app returns from the background while this view is shown (see
    /// `ForegroundRefresh`). A screen passes its shared ViewModel's refresh, e.g.
    /// `.refreshOnForeground { viewModel.refresh() }`.
    func refreshOnForeground(_ refresh: @escaping () -> Void) -> some View {
        modifier(RefreshOnForeground(refresh: refresh))
    }
}

/// Feeds the scene's phase changes to one `ForegroundRefresh` that lives as long as the view.
private struct RefreshOnForeground: ViewModifier {
    let refresh: () -> Void

    @Environment(\.scenePhase) private var scenePhase
    @State private var foregroundRefresh: ForegroundRefresh?

    func body(content: Content) -> some View {
        content.onChange(of: scenePhase, initial: true) { _, phase in
            // Created on the first phase (the screen appearing), with the screen's ViewModel, which its
            // IosViewModelStoreOwner keeps for the screen's whole life.
            var tracker = foregroundRefresh ?? ForegroundRefresh(refresh: refresh)
            tracker.scenePhaseChanged(to: phase)
            foregroundRefresh = tracker
        }
    }
}
