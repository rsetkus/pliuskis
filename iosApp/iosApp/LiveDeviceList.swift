import Shared
import SwiftUI

/// The device list of the running app. It owns the shared `DevicesViewModel` and `NetworkStatusViewModel` through
/// `IosViewModelStoreOwner` and passes their current states to `DeviceListScreen`; Retry calls the ViewModel's
/// `retry()`. While the screen is shown it collects both states; a pushed control screen keeps the owner alive, so
/// going back shows the same ViewModels. When the app returns from the background, the list refreshes
/// (`DevicesViewModel.refresh()`: reconnect if needed; the broker then replays the retained device state).
struct LiveDeviceList: View {
    static let devicesViewModelKey = "devices"
    static let networkStatusViewModelKey = "networkStatus"

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
            onRetry: { devices.retry() }
        )
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
