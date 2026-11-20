import SwiftUI

/// The first screen: the device list inside the app's navigation. In the Debug build, the launch argument
/// `-uiTestScenario <name>` replaces the live ViewModels with a fixed UI test scenario (see `UITestScenario`).
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
        DevicesNavigation { selection in
            LiveDeviceList(selection: selection)
        } control: { route in
            LiveControlScreen(deviceId: route.deviceId, deviceName: route.deviceName)
        }
    }
}
