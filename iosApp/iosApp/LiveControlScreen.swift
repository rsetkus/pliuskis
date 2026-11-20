import Shared
import SwiftUI

/// The control screen of the running app. It owns the shared `ControlViewModel` of `deviceId` through
/// `IosViewModelStoreOwner`, collects its status and watering states while it is shown and passes them to
/// `ControlScreen`; the water button calls `waterPlant()`, Retry calls `retry()`. Opening the screen connects if needed, asks the device for its status and subscribes to the
/// reply (all in Kotlin). When the app returns from the background, it asks again (`ControlViewModel.refresh()`; status
/// replies are not retained). Going back releases the owner, which clears the ViewModel: its subscription ends.
struct LiveControlScreen: View {
    static let controlViewModelKey = "control"

    let deviceId: String
    let deviceName: String

    @StateObject private var owner = IosViewModelStoreOwner()
    @State private var state: ControlUiState?
    @State private var wateringState: WateringState?

    var body: some View {
        let viewModel: ControlViewModel = owner.viewModel(key: Self.controlViewModelKey) {
            SharedEntryPointKt.createControlViewModel(deviceId: deviceId)
        }
        ControlScreen(
            deviceName: deviceName,
            state: state ?? viewModel.controlUiState.value,
            wateringState: wateringState ?? viewModel.wateringState.value,
            onWater: { viewModel.waterPlant() },
            onRetry: { viewModel.retry() }
        )
        .refreshOnForeground { viewModel.refresh() }
        .task {
            for await latest in viewModel.controlUiState {
                state = latest
            }
        }
        .task {
            for await latest in viewModel.wateringState {
                wateringState = latest
            }
        }
    }
}
