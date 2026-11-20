import SwiftUI

/// The selected device: its id for the ViewModel, its name for the title.
struct ControlRoute: Hashable {
    let deviceId: String
    let deviceName: String
}

/// The app's navigation, the iOS counterpart of Android's list-detail layout: a `NavigationSplitView` with the device
/// list as the sidebar and the selected device's control screen as the detail. On iPhone (compact width) it collapses
/// into a stack, so a tapped row pushes the control screen and Back pops it. On iPad both show side by side. The live
/// app passes `LiveControlScreen`; the Debug UI test scenarios pass a control screen with a fixed state. Both use the
/// same navigation, so the tests tap through the real thing.
struct DevicesNavigation<DeviceList: View, Control: View>: View {
    @ViewBuilder let deviceList: (_ selection: Binding<ControlRoute?>) -> DeviceList
    @ViewBuilder let control: (_ route: ControlRoute) -> Control

    @State private var selection: ControlRoute?

    var body: some View {
        NavigationSplitView {
            deviceList($selection)
        } detail: {
            if let selection {
                control(selection)
                    // A new screen, with its own ViewModel, for each device.
                    .id(selection.deviceId)
            } else {
                NoDeviceSelected()
            }
        }
        .navigationSplitViewStyle(.balanced)
    }
}

/// The detail column on iPad before a device is chosen.
private struct NoDeviceSelected: View {
    var body: some View {
        ContentUnavailableView(
            "Choose a device",
            systemImage: "leaf",
            description: Text("Pick a device from your garden to see how your plant is doing.")
        )
    }
}
