import Shared
import SwiftUI

/// The control screen of one device, drawn from the state the shared `ControlViewModel` produces: a progress indicator
/// while loading, the status (soil humidity, water tank, last update) with the water button, or the error view. The
/// states and the water button follow the Android control screen: the button exists only with a status, and a tap
/// changes nothing on screen (there is no separate watering state). The screen holds no state and no logic of its own;
/// the status thresholds live in Kotlin, and `ControlStatusText` turns the values into words. The update time follows
/// the environment's `\.locale` and `\.timeZone`.
struct ControlScreen: View {
    let deviceId: String
    let state: ControlUiState
    let onWater: () -> Void

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("control.screen")
            .navigationTitle(deviceId)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text(deviceId)
                        .font(.headline)
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityIdentifier("control.deviceId")
                }
            }
    }

    @ViewBuilder
    private var content: some View {
        switch onEnum(of: state) {
        case .loading:
            ProgressView("Loading status…")
                .accessibilityIdentifier("control.loading")
        case .success(let success):
            DeviceStatusView(status: success.data, onWater: onWater)
        case .error:
            ContentUnavailableView {
                Label("Couldn't get the status", systemImage: "exclamationmark.triangle")
            } description: {
                Text("The device didn't send a status the app can read. Go back and try again.")
            }
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("control.error")
        }
    }
}

/// One status reading: soil humidity, water tank and the reading time, with the water button below.
private struct DeviceStatusView: View {
    let status: DeviceStatus
    let onWater: () -> Void

    @Environment(\.locale) private var locale
    @Environment(\.timeZone) private var timeZone

    var body: some View {
        List {
            Section {
                StatusRow(
                    title: "Soil humidity",
                    systemImage: "humidity.fill",
                    value: ControlStatusText.humidity(status.humidity),
                    needsAttention: status.humidity == .thirsty
                )
                .accessibilityIdentifier("control.humidity")
                StatusRow(
                    title: "Water tank",
                    systemImage: "drop.fill",
                    value: ControlStatusText.waterLevel(status.waterLevel),
                    needsAttention: status.waterLevel == .low
                )
                .accessibilityIdentifier("control.waterLevel")
            } footer: {
                Text("Last update: \(ControlStatusText.updatedAt(status.updatedAt, locale: locale, timeZone: timeZone))")
                    .accessibilityIdentifier("control.updatedAt")
            }
        }
        .safeAreaInset(edge: .bottom) {
            Button(action: onWater) {
                Label("Water plant", systemImage: "drop.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .padding()
            .background(.bar)
            .accessibilityIdentifier("control.water")
        }
    }
}

/// A status value with its name and icon; a value that needs the user's attention (thirsty, low) is shown in orange.
private struct StatusRow: View {
    let title: LocalizedStringKey
    let systemImage: String
    let value: String
    let needsAttention: Bool

    var body: some View {
        HStack {
            Label(title, systemImage: systemImage)
            Spacer()
            Text(value)
                .foregroundStyle(needsAttention ? Color.orange : Color.secondary)
                .multilineTextAlignment(.trailing)
        }
        .accessibilityElement(children: .combine)
    }
}
