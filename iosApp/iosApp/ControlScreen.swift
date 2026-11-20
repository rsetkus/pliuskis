import Shared
import SwiftUI

/// The control screen of one device, drawn from the state the shared `ControlViewModel` produces: a progress indicator
/// while loading; the health card, the two readings and the water button with the status; or the error view with
/// Retry. It mirrors Android's control screen in layout, colors and words. The water button follows
/// `wateringState`: "Watering…" while sending, "Watering started" with a check (and a success haptic) once the broker
/// takes the command, "Try again" with an error line after a failure. The screen holds no state and no logic of its
/// own: the thresholds and the watering state live in Kotlin, `ControlStatusText` turns the values into words. The
/// update time follows the environment's `\.locale` and `\.timeZone`.
struct ControlScreen: View {
    let deviceName: String
    let state: ControlUiState
    let wateringState: WateringState
    let onWater: () -> Void
    let onRetry: () -> Void

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Flora.canvas)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("control.screen")
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text(title)
                        .font(.headline)
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityIdentifier("control.title")
                }
            }
    }

    private var title: String {
        deviceName.isEmpty ? String(localized: "Plant") : deviceName
    }

    @ViewBuilder
    private var content: some View {
        switch onEnum(of: state) {
        case .loading:
            ProgressView("Loading the plant's status…")
                .accessibilityIdentifier("control.loading")
        case .success(let success):
            DeviceStatusView(status: success.data, wateringState: wateringState, onWater: onWater)
        case .error:
            ContentUnavailableView {
                // The identifier sits on the title: the screen-wide container (`control.screen`) would hide it on the
                // whole view, and Retry has to stay its own button.
                Label("Can't reach your plant", systemImage: "exclamationmark.triangle")
                    .accessibilityIdentifier("control.error")
            } description: {
                Text("Check your internet connection and try again.")
            } actions: {
                Button("Retry", action: onRetry)
                    .floraProminentButton(tint: Flora.leaf)
                    .accessibilityIdentifier("control.retry")
            }
        }
    }
}

/// One status reading: the health card, "Conditions" with the two reading tiles, and the water button pinned to the
/// bottom, where the thumb rests.
private struct DeviceStatusView: View {
    let status: DeviceStatus
    let wateringState: WateringState
    let onWater: () -> Void

    @Environment(\.locale) private var locale
    @Environment(\.timeZone) private var timeZone

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Flora.Spacing.lg) {
                HealthCard(
                    humidity: status.humidity,
                    updatedAt: ControlStatusText.updatedAt(status.updatedAt, locale: locale, timeZone: timeZone)
                )
                VStack(alignment: .leading, spacing: Flora.Spacing.sm) {
                    Text("Conditions")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .textCase(.uppercase)
                        .accessibilityAddTraits(.isHeader)
                    HStack(spacing: Flora.Spacing.sm) {
                        ReadingTile(
                            title: "Soil moisture",
                            systemImage: "humidity.fill",
                            value: ControlStatusText.percent(status.soilMoisturePercent, locale: locale),
                            progress: Double(status.soilMoisturePercent) / 100,
                            caption: ControlStatusText.humidity(status.humidity),
                            tone: status.humidity == .thirsty ? Flora.clay : Flora.leaf
                        )
                        .accessibilityIdentifier("control.humidity")
                        ReadingTile(
                            title: "Water tank",
                            systemImage: "drop.fill",
                            value: ControlStatusText.percent(status.waterLevelPercent, locale: locale),
                            progress: Double(status.waterLevelPercent) / 100,
                            caption: ControlStatusText.waterLevel(status.waterLevel),
                            tone: status.waterLevel == .low ? Flora.clay : Flora.rain
                        )
                        .accessibilityIdentifier("control.waterLevel")
                    }
                }
            }
            .padding(Flora.Spacing.md)
        }
        .safeAreaInset(edge: .bottom) {
            WaterAction(wateringState: wateringState, onWater: onWater)
        }
    }
}

/// The one thing to notice first: is the plant fine or thirsty. Leaf when healthy, Clay when thirsty; the colors
/// change with a spring when a new reading flips the state.
private struct HealthCard: View {
    let humidity: HumidityStatus
    let updatedAt: String

    private var thirsty: Bool { humidity == .thirsty }

    var body: some View {
        VStack(alignment: .leading, spacing: Flora.Spacing.xxs) {
            Image(systemName: thirsty ? "drop.degreesign.slash" : "leaf.fill")
                .font(.title)
                .foregroundStyle(thirsty ? Flora.clay : Flora.leaf)
                .contentTransition(.symbolEffect(.replace))
                .padding(.bottom, Flora.Spacing.xs)
            Text(ControlStatusText.healthTitle(humidity))
                .font(.title2.weight(.semibold))
            Text(ControlStatusText.healthMessage(humidity))
                .font(.subheadline)
            Text("Updated \(updatedAt)")
                .font(.footnote.weight(.medium))
                .foregroundStyle(thirsty ? Flora.clay : Flora.leaf)
                .padding(.top, Flora.Spacing.sm)
                .accessibilityIdentifier("control.updatedAt")
        }
        .foregroundStyle(thirsty ? Flora.onClayContainer : Flora.onLeafContainer)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Flora.Spacing.lg)
        .background(
            thirsty ? Flora.clayContainer : Flora.leafContainer,
            in: RoundedRectangle(cornerRadius: Flora.Radius.healthCard, style: .continuous)
        )
        .animation(.smooth, value: humidity)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("control.health")
    }
}

/// One reading: a small symbol and title, the value large, a meter and a short caption in the tone color. Read by
/// VoiceOver as one item: "Soil moisture, 30%, Dry".
private struct ReadingTile: View {
    let title: LocalizedStringKey
    let systemImage: String
    let value: String
    let progress: Double
    let caption: String
    let tone: Color

    var body: some View {
        VStack(alignment: .leading, spacing: Flora.Spacing.xs) {
            Label(title, systemImage: systemImage)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .lineLimit(1)
            Text(value)
                .font(.system(.largeTitle, design: .rounded).weight(.medium).monospacedDigit())
                .contentTransition(.numericText())
            ProgressView(value: min(max(progress, 0), 1))
                .tint(tone)
                .background(Flora.track, in: Capsule())
            Text(caption)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(tone)
                .padding(.top, Flora.Spacing.xxs)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Flora.Spacing.md)
        .background(Flora.card, in: RoundedRectangle(cornerRadius: Flora.Radius.tile, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

/// "Water now", a prominent Liquid Glass button in Rain on iOS 26 (bordered prominent before), with its feedback.
/// A tap while sending does nothing; Kotlin also ignores it.
private struct WaterAction: View {
    let wateringState: WateringState
    let onWater: () -> Void

    var body: some View {
        VStack(spacing: Flora.Spacing.sm) {
            if wateringState == .failed {
                Text("Couldn't reach your plant. Try again.")
                    .font(.subheadline)
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.center)
                    .transition(.opacity)
                    .accessibilityIdentifier("control.wateringFailed")
            }
            Button {
                if wateringState != .sending { onWater() }
            } label: {
                label
                    .font(.headline)
                    .foregroundStyle(Flora.onRain)
                    .frame(maxWidth: .infinity, minHeight: 32)
            }
            .controlSize(.large)
            .floraProminentButton(tint: Flora.rain)
            .accessibilityIdentifier("control.water")
        }
        .padding(.horizontal, Flora.Spacing.md)
        .padding(.bottom, Flora.Spacing.xs)
        .animation(.smooth, value: wateringState)
        .sensoryFeedback(.success, trigger: wateringState) { _, new in new == .started }
        .sensoryFeedback(.error, trigger: wateringState) { _, new in new == .failed }
    }

    @ViewBuilder
    private var label: some View {
        switch wateringState {
        case .sending:
            HStack(spacing: Flora.Spacing.xs) {
                ProgressView()
                    .tint(Flora.onRain)
                Text("Watering…")
            }
        case .started:
            Label("Watering started", systemImage: "checkmark.circle.fill")
                .symbolEffect(.bounce, value: wateringState)
        case .failed:
            Label("Try again", systemImage: "drop.fill")
        default:
            Label("Water now", systemImage: "drop.fill")
        }
    }
}
