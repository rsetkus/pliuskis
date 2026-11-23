import Shared
import SwiftUI

/// The device list of the running app. It owns the shared `DevicesViewModel` and `NetworkStatusViewModel` through
/// `IosViewModelStoreOwner` and passes their current states to `DeviceListScreen`; Retry and Search again call the
/// ViewModel's `retry()`. While the screen is shown it collects both states; an open control screen keeps the owner
/// alive, so going back shows the same ViewModels. When the app returns from the background, the list refreshes
/// (`DevicesViewModel.refresh()`: reconnect if needed; the broker then replays the retained device state). `selection`
/// is the selected device of `DevicesNavigation`. With `account`, the toolbar has the account menu (sign out, delete
/// account).
struct LiveDeviceList: View {
    static let devicesViewModelKey = "devices"
    static let networkStatusViewModelKey = "networkStatus"

    @Binding var selection: ControlRoute?
    var account: AccountActions?

    @StateObject private var owner = IosViewModelStoreOwner()
    @State private var state: DevicesListScreenState?
    @State private var isOffline = false

    var body: some View {
        let devices: DevicesViewModel = owner.viewModel(key: Self.devicesViewModelKey) {
            SharedEntryPointKt.createDevicesViewModel()
        }
        let networkStatus: NetworkStatusViewModel = owner.viewModel(key: Self.networkStatusViewModelKey) {
            SharedEntryPointKt.createNetworkStatusViewModel()
        }
        DeviceListScreen(
            state: state ?? devices.devices.value,
            isOffline: isOffline,
            selection: $selection,
            onRetry: { devices.retry() }
        )
        .toolbar {
            if let account {
                ToolbarItem(placement: .topBarTrailing) {
                    AccountMenu(actions: account)
                }
            }
        }
        .refreshOnForeground { devices.refresh() }
        .task {
            for await latest in devices.devices {
                state = latest
            }
        }
        .task {
            for await offline in networkStatus.isOffline {
                isOffline = offline.boolValue
            }
        }
    }
}
