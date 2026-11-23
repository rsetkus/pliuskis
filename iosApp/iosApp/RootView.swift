import SwiftUI

/// The first screen: sign-in or the device list, following the session (`LiveSession`). In the Debug build, the launch
/// argument `-uiTestScenario <name>` replaces the live ViewModels with a fixed UI test scenario (see `UITestScenario`);
/// the device and control scenarios open without sign-in.
struct RootView: View {
    var body: some View {
        #if DEBUG
        if let scenario = UITestScenario.fromLaunchArguments() {
            UITestScenarioView(scenario: scenario)
        } else {
            live
        }
        #else
        live
        #endif
    }

    private var live: some View {
        LiveSession()
    }
}
