import Shared
import SwiftUI

/// The device list, drawn from the state the shared `DevicesViewModel` produces: a progress indicator while loading,
/// the devices in the order Kotlin sends them (already sorted by name), the empty state, or the error view with Retry.
/// The offline banner sits above the content while `isOffline` is true. It holds no state and no logic of its own:
/// sorting, the empty-list timeout and retry all live in Kotlin. A tapped row pushes a `ControlRoute`, which
/// `DevicesNavigation` turns into the control screen.
struct DeviceListScreen: View {
    let state: DevicesListScreenState
    let isOffline: Bool
    let onRetry: () -> Void

    var body: some View {
        content
            .navigationTitle("Devices")
            .safeAreaInset(edge: .top, spacing: 0) {
                if isOffline {
                    OfflineBanner()
                }
            }
            .animation(.default, value: isOffline)
            .toolbar {
                ToolbarItem(placement: .status) {
                    DeviceListStatus(state: state)
                }
            }
    }

    @ViewBuilder
    private var content: some View {
        switch onEnum(of: state) {
        case .loading:
            ProgressView("Loading devices…")
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .accessibilityIdentifier("deviceList.loading")
        case .success(let success) where success.devices.isEmpty:
            ContentUnavailableView(
                "No devices found",
                systemImage: "leaf",
                description: Text("Make sure your device is powered on and connected.")
            )
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("deviceList.empty")
        case .success(let success):
            List(success.devices, id: \.id) { device in
                NavigationLink(value: ControlRoute(deviceId: device.id)) {
                    DeviceRow(device: device)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(device.name)
                .accessibilityValue(device.online ? "Online" : "Offline")
                .accessibilityAddTraits(.isButton)
                .accessibilityIdentifier("deviceList.row.\(device.id)")
            }
        case .error:
            ContentUnavailableView {
                Label("Couldn't load your devices", systemImage: "exclamationmark.triangle")
            } description: {
                Text("Check your connection and try again.")
            } actions: {
                Button("Retry", action: onRetry)
                    .buttonStyle(.borderedProminent)
                    .accessibilityIdentifier("deviceList.retry")
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("deviceList.error")
        }
    }
}

/// One device: its type icon, its name and whether it is online.
private struct DeviceRow: View {
    let device: DeviceListItem

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: device.type == .plant ? "leaf.fill" : "questionmark.circle")
                .foregroundStyle(device.online ? Color.green : Color.secondary)
                .frame(width: 28)
            Text(device.name)
                .font(.body)
            Spacer()
            HStack(spacing: 6) {
                Circle()
                    .fill(device.online ? Color.green : Color.gray)
                    .frame(width: 8, height: 8)
                Text(device.online ? "Online" : "Offline")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

/// Shown above the device list while the phone has no network. It does not replace the list.
private struct OfflineBanner: View {
    var body: some View {
        Label("You are not connected to the internet", systemImage: "wifi.slash")
            .font(.subheadline.weight(.medium))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .padding(.horizontal)
            // Only behind the banner itself; by default a background spreads under the navigation bar.
            .background(Color.orange, ignoresSafeAreaEdges: [])
            .transition(.move(edge: .top).combined(with: .opacity))
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("deviceList.offlineBanner")
    }
}

/// A one-line summary of the list state in the bottom toolbar, like Mail's "Updated Just Now": what the Kotlin state
/// says, as text. STORY-1's launch UI test finds it by the identifier `story1.kotlinValue`, which marks the first
/// screen's value that comes from the shared framework.
private struct DeviceListStatus: View {
    let state: DevicesListScreenState

    var body: some View {
        Text(text)
            .font(.caption)
            .foregroundStyle(.secondary)
            .lineLimit(1)
            .fixedSize()
            .accessibilityIdentifier("story1.kotlinValue")
    }

    private var text: String {
        switch onEnum(of: state) {
        case .loading:
            "Looking for devices…"
        case .success(let success):
            switch success.devices.count {
            case 0: "No devices"
            case 1: "1 device"
            case let count: "\(count) devices"
            }
        case .error:
            "Not connected"
        }
    }
}
