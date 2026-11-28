#if DEBUG
import Observation
import Shared
import SwiftUI

/// Fixed screen states for the UI tests (`iosAppUITests`), chosen with the launch argument `-uiTestScenario <name>`.
/// The scenario feeds `DeviceListScreen` and `ControlScreen`, inside the real `DevicesNavigation`, with Kotlin state
/// objects instead of the live ViewModels, so a test sees exactly the state it names; the `signIn*` scenarios feed
/// `SignInScreen` the same way. Every scenario skips the session: the device and control scenarios open without
/// sign-in. Debug build only.
enum UITestScenario: String {
    /// `Loading`, online.
    case devicesLoading
    /// `Success` with basil (b1, online) and fern (f1, offline), in Kotlin's sorted order, online.
    case devicesSuccess
    /// `Success` with no devices, online.
    case devicesEmpty
    /// `Error`, online; Retry counts its calls in `uiTestScenario.retryCount` and the state stays `Error`.
    case devicesError
    /// `Success` with basil (b1, online), offline; the button `uiTestScenario.goOnline` switches to online.
    case devicesOffline
    /// `Success` with thyme (t1), basil (b1) and sage (s1) in that (unsorted) order, online.
    case devicesUnsorted
    /// List with basil (b1, online); its control screen shows `Success` THIRSTY (30%), LOW (20%),
    /// 2026-11-19T10:15:00Z, watering IDLE.
    case controlStatus
    /// Like `controlStatus`, watering SENDING.
    case controlWatering
    /// Like `controlStatus`, watering FAILED.
    case controlWateringFailed
    /// List with basil (b1, online); its control screen shows `Loading`.
    case controlLoading
    /// List with basil (b1, online); its control screen shows `Error("UI test scenario error")`; Retry counts its calls
    /// in `uiTestScenario.retryCount`.
    case controlError
    /// List with basil (b1, online); its control screen starts `Loading`, and `uiTestScenario.nextControlState` moves
    /// it to the `controlStatus` state, then to the `controlError` state.
    case controlStateSequence
    /// The sign-in screen in `SignInStateSignedOut`; the Google button counts its taps in `uiTestScenario.googleCount`,
    /// Apple's button in `uiTestScenario.appleCount`.
    case signInSignedOut
    /// The sign-in screen in `SignInStateSigningIn(provider: GOOGLE)`.
    case signInSigningIn
    /// The sign-in screen in `SignInStateError(reason: FAILED)`.
    case signInError
    /// The live device list and control screens (the shared ViewModels and the real MQTT client), with a fake
    /// signed-in session: no sign-in screen and no account menu.
    case signedInLive

    /// The scenario named by `-uiTestScenario`, or nil for the live app. The launch argument lands in the argument
    /// domain of the standard UserDefaults.
    static func fromLaunchArguments(_ defaults: UserDefaults = .standard) -> UITestScenario? {
        defaults.string(forKey: "uiTestScenario").flatMap(UITestScenario.init(rawValue:))
    }

    var state: DevicesListScreenState {
        switch self {
        case .devicesLoading:
            DevicesListScreenStateLoading.shared
        case .devicesSuccess:
            Self.success([Self.device("basil", "b1", online: true), Self.device("fern", "f1", online: false)])
        case .devicesEmpty:
            Self.success([])
        case .devicesError:
            DevicesListScreenStateError(error: KotlinThrowable(message: "UI test scenario error"))
        case .devicesOffline, .controlStatus, .controlWatering, .controlWateringFailed, .controlLoading, .controlError,
             .controlStateSequence:
            Self.success([Self.device("basil", "b1", online: true)])
        case .devicesUnsorted:
            Self.success([
                Self.device("thyme", "t1", online: true),
                Self.device("basil", "b1", online: true),
                Self.device("sage", "s1", online: true),
            ])
        case .signInSignedOut, .signInSigningIn, .signInError, .signedInLive:
            // These scenarios show no fixed device list.
            DevicesListScreenStateLoading.shared
        }
    }

    /// The sign-in screen's state, or nil for the device and control scenarios.
    var signInState: SignInState? {
        switch self {
        case .signInSignedOut: SignInStateSignedOut.shared
        case .signInSigningIn: SignInStateSigningIn(provider: .google)
        case .signInError: SignInStateError(reason: .failed)
        default: nil
        }
    }

    var startsOffline: Bool {
        self == .devicesOffline
    }

    /// The control screen's states in the order `uiTestScenario.nextControlState` steps through them; the device list
    /// scenarios open a control screen that stays `Loading`.
    var controlStates: [ControlUiState] {
        switch self {
        case .controlStatus, .controlWatering, .controlWateringFailed:
            [Self.thirstyAndLow]
        case .controlError:
            [Self.controlFailure]
        case .controlStateSequence:
            [ControlUiStateLoading.shared, Self.thirstyAndLow, Self.controlFailure]
        default:
            [ControlUiStateLoading.shared]
        }
    }

    /// The water button's state.
    var wateringState: WateringState {
        switch self {
        case .controlWatering: .sending
        case .controlWateringFailed: .failed
        default: .idle
        }
    }

    /// The control screen's state after `step` taps on `uiTestScenario.nextControlState`; it stays on the last one.
    func controlState(step: Int) -> ControlUiState {
        controlStates[min(step, controlStates.count - 1)]
    }

    /// 2026-11-19T10:15:00Z, 12:15 in Europe/Vilnius.
    private static let thirstyAndLow: ControlUiState = ControlUiStateSuccess(
        data: DeviceStatus(
            humidity: .thirsty,
            waterLevel: .low,
            updatedAt: KotlinInstant.companion.fromEpochMilliseconds(epochMilliseconds: 1_795_083_300_000),
            soilMoisturePercent: 30,
            waterLevelPercent: 20
        )
    )

    private static let controlFailure: ControlUiState = ControlUiStateError(message: "UI test scenario error")

    private static func success(_ devices: [DeviceListItem]) -> DevicesListScreenState {
        DevicesListScreenStateSuccess(devices: devices)
    }

    private static func device(_ name: String, _ id: String, online: Bool) -> DeviceListItem {
        DeviceListItem(name: name, id: id, type: .plant, online: online)
    }
}

/// What the UI tests read and drive outside the navigation stack, so it stays on screen after Back.
@Observable
final class UITestScenarioRecorder {
    var retryCount = 0
    var waterCount = 0
    var controlClearedCount = 0
    var controlStep = 0
    var googleCount = 0
    var appleCount = 0
}

/// Shows a `UITestScenario`: the sign-in screen for the `signIn*` scenarios, the live devices for `signedInLive`, and
/// otherwise the scenario in the app's navigation.
struct UITestScenarioView: View {
    let scenario: UITestScenario

    var body: some View {
        if let signInState = scenario.signInState {
            UITestSignInScenarioView(state: signInState)
        } else if scenario == .signedInLive {
            LiveDevices(account: nil)
        } else {
            UITestDevicesScenarioView(scenario: scenario)
        }
    }
}

/// The sign-in screen in a fixed state, with its Google and Apple calls counted below it.
private struct UITestSignInScenarioView: View {
    let state: SignInState

    @State private var recorder = UITestScenarioRecorder()

    var body: some View {
        SignInScreen(
            state: state,
            onGoogle: { recorder.googleCount += 1 },
            onApple: { recorder.appleCount += 1 }
        )
        .safeAreaInset(edge: .bottom) {
            HStack {
                Text("\(recorder.googleCount)")
                    .accessibilityIdentifier("uiTestScenario.googleCount")
                Text("\(recorder.appleCount)")
                    .accessibilityIdentifier("uiTestScenario.appleCount")
                Spacer()
            }
            .font(.caption)
            .padding(.horizontal)
            .background(.bar)
        }
    }
}

/// Shows a device or control `UITestScenario` in the app's navigation, with test controls below it: the retry, water
/// and cleared counts, the control state step (`controlStateSequence` only) and a button that brings the fake
/// connectivity online.
private struct UITestDevicesScenarioView: View {
    let scenario: UITestScenario

    @State private var recorder = UITestScenarioRecorder()
    @State private var isOffline: Bool

    init(scenario: UITestScenario) {
        self.scenario = scenario
        _isOffline = State(initialValue: scenario.startsOffline)
    }

    var body: some View {
        DevicesNavigation { selection in
            DeviceListScreen(
                state: scenario.state,
                isOffline: isOffline,
                selection: selection,
                onRetry: { recorder.retryCount += 1 }
            )
        } control: { route in
            UITestScenarioControlScreen(route: route, scenario: scenario, recorder: recorder)
        }
        .safeAreaInset(edge: .bottom) {
            HStack {
                Text("\(recorder.retryCount)")
                    .accessibilityIdentifier("uiTestScenario.retryCount")
                Text("\(recorder.waterCount)")
                    .accessibilityIdentifier("uiTestScenario.waterCount")
                Text("\(recorder.controlClearedCount)")
                    .accessibilityIdentifier("uiTestScenario.controlClearedCount")
                Spacer()
                if scenario == .controlStateSequence {
                    Button("Next state") { recorder.controlStep += 1 }
                        .accessibilityIdentifier("uiTestScenario.nextControlState")
                }
                Button("Go online") { isOffline = false }
                    .accessibilityIdentifier("uiTestScenario.goOnline")
            }
            .font(.caption)
            .padding(.horizontal)
            .background(.bar)
        }
    }
}

/// The scenario's control screen: `ControlScreen` with the scenario's state and watering state, water and Retry actions
/// that count their calls,
/// en_GB and Europe/Vilnius as locale and time zone. Like `LiveControlScreen` owns `ControlViewModel`, it owns a
/// ViewModel through `IosViewModelStoreOwner` with `@StateObject`; that one counts `onCleared`, so a test sees the
/// screen's ViewModel cleared when Back pops the screen.
private struct UITestScenarioControlScreen: View {
    let route: ControlRoute
    let scenario: UITestScenario
    let recorder: UITestScenarioRecorder

    @StateObject private var owner = IosViewModelStoreOwner()

    var body: some View {
        let _: ClearCountingViewModel = owner.viewModel(key: "uiTestScenario.control") {
            ClearCountingViewModel(recorder: recorder)
        }
        ControlScreen(
            deviceName: route.deviceName,
            state: scenario.controlState(step: recorder.controlStep),
            wateringState: scenario.wateringState,
            onWater: { recorder.waterCount += 1 },
            onRetry: { recorder.retryCount += 1 }
        )
        .environment(\.locale, Locale(identifier: "en_GB"))
        .environment(\.timeZone, TimeZone(identifier: "Europe/Vilnius") ?? .gmt)
    }
}

/// A shared (Kotlin) ViewModel subclassed in Swift that records each `onCleared` in the scenario's recorder.
private final class ClearCountingViewModel: Lifecycle_viewmodelViewModel {
    private let recorder: UITestScenarioRecorder

    init(recorder: UITestScenarioRecorder) {
        self.recorder = recorder
        super.init()
    }

    override func onCleared() {
        recorder.controlClearedCount += 1
        super.onCleared()
    }
}
#endif
