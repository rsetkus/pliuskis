import Shared
import SwiftUI

/// "Your garden": the device list, drawn from the state the shared `DevicesViewModel` produces. A progress indicator
/// while loading, the devices in the order Kotlin sends them (already sorted by name), the empty state with "Search
/// again", or the error view with Retry; both actions call `onRetry` (the ViewModel's `retry()`). The offline banner, a
/// Liquid Glass capsule on iOS 26, floats above the content while `isOffline` is true. A one-line summary sits in the
/// bottom toolbar ("3 devices · 2 online"). It holds no state and no logic of its own: sorting, the empty-list
/// timeout and retry all live in Kotlin. A tapped row selects a `ControlRoute` in `DevicesNavigation`.
struct DeviceListScreen: View {
    let state: DevicesListScreenState
    let isOffline: Bool
    @Binding var selection: ControlRoute?
    let onRetry: () -> Void

    var body: some View {
        content
            .navigationTitle("Your garden")
            .background(Flora.canvas)
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
            ProgressView("Looking for your devices…")
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .accessibilityIdentifier("deviceList.loading")
        case .success(let success) where success.devices.isEmpty:
            ContentUnavailableView {
                Label("No devices yet", systemImage: "leaf")
            } description: {
                Text("Switch on your Pliuškis device and keep it near Wi-Fi. It shows up here on its own.")
            } actions: {
                Button("Search again", action: onRetry)
                    .floraProminentButton(tint: Flora.leaf)
                    .accessibilityIdentifier("deviceList.searchAgain")
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("deviceList.empty")
        case .success(let success):
            List(selection: $selection) {
                ForEach(success.devices, id: \.id) { device in
                    NavigationLink(value: ControlRoute(deviceId: device.id, deviceName: device.name)) {
                        DeviceRow(device: device)
                    }
                    .listRowBackground(Flora.card)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(device.name)
                    .accessibilityValue(device.online ? "Online" : "Offline")
                    .accessibilityAddTraits(.isButton)
                    .accessibilityIdentifier("deviceList.row.\(device.id)")
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
        case .error:
            ContentUnavailableView {
                Label("Couldn't load your devices", systemImage: "wifi.exclamationmark")
            } description: {
                Text("Check your internet connection and try again.")
            } actions: {
                Button("Retry", action: onRetry)
                    .floraProminentButton(tint: Flora.leaf)
                    .accessibilityIdentifier("deviceList.retry")
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("deviceList.error")
        }
    }
}

/// One device: its type symbol in a tinted circle (Leaf while online), its name and whether it is online. Offline
/// devices use neutral colors instead of fading, so they stay readable.
private struct DeviceRow: View {
    let device: DeviceListItem

    var body: some View {
        HStack(spacing: Flora.Spacing.sm) {
            Image(systemName: device.type == .plant ? "leaf.fill" : "questionmark")
                .font(.body.weight(.semibold))
                .foregroundStyle(device.online ? Flora.onLeafContainer : Color.secondary)
                .frame(width: 36, height: 36)
                .background(device.online ? Flora.leafContainer : Color(.tertiarySystemFill), in: Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text(device.name)
                    .font(.body)
                Text(device.online ? "Online" : "Offline")
                    .font(.subheadline)
                    .foregroundStyle(device.online ? Flora.leaf : Color.secondary)
            }
        }
        .padding(.vertical, Flora.Spacing.xxs)
    }
}

/// Floats above the device list while the phone has no network. It does not replace the list.
private struct OfflineBanner: View {
    var body: some View {
        Label("You're offline. Check your internet connection.", systemImage: "wifi.slash")
            .font(.subheadline.weight(.medium))
            .foregroundStyle(Flora.onClayContainer)
            .padding(.vertical, 10)
            .padding(.horizontal, Flora.Spacing.md)
            .floraGlassCapsule(tint: Flora.clayContainer.opacity(0.6))
            .padding(.horizontal, Flora.Spacing.md)
            .padding(.vertical, Flora.Spacing.xs)
            .transition(.move(edge: .top).combined(with: .opacity))
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("deviceList.offlineBanner")
    }
}

/// A one-line summary of the list state in the bottom toolbar, like Mail's "Updated Just Now": what the Kotlin state
/// says, as text ("3 devices · 2 online"). On iOS 26 the system draws the toolbar as Liquid Glass. STORY-1's launch UI
/// test finds it by the identifier `story1.kotlinValue`, which marks the first screen's value that comes from the
/// shared framework.
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
            String(localized: "Looking for devices…")
        case .success(let success):
            Self.summary(devices: success.devices)
        case .error:
            String(localized: "Not connected")
        }
    }

    private static func summary(devices: [DeviceListItem]) -> String {
        let online = devices.filter(\.online).count
        let total = switch devices.count {
        case 0: String(localized: "No devices")
        case 1: String(localized: "1 device")
        case let count: String(localized: "\(count) devices")
        }
        return devices.isEmpty ? total : String(localized: "\(total) · \(online) online")
    }
}
