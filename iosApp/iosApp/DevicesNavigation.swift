import SwiftUI

/// Where a tapped device row goes: the control screen of that device.
struct ControlRoute: Hashable {
    let deviceId: String
}

/// The app's navigation: the device list at the root of a `NavigationStack`, and the control screen pushed for a
/// tapped device. The live app passes `LiveControlScreen`; the Debug UI test scenarios pass a control screen with a
/// fixed state. Both use the same stack, so the tests tap through the real navigation.
struct DevicesNavigation<DeviceList: View, Control: View>: View {
    @ViewBuilder let deviceList: () -> DeviceList
    @ViewBuilder let control: (_ deviceId: String) -> Control

    var body: some View {
        NavigationStack {
            deviceList()
                .navigationDestination(for: ControlRoute.self) { route in
                    control(route.deviceId)
                }
        }
    }
}
