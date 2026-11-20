import Shared
import SwiftUI

/// The iOS app. It starts the shared (Kotlin) graph once, before the first screen asks for a ViewModel, with the
/// broker settings (host from the xcconfig, this install's client ID) and the CocoaMQTT socket the Kotlin MQTT client
/// runs on.
@main
struct PliuskisApp: App {

    init() {
        let settings = BrokerConfiguration.settings(
            infoDictionary: Bundle.main.infoDictionary,
            clientId: InstallClientIdKt.installClientId()
        )
        SharedEntryPointKt.doInitShared(settings: settings, transport: CocoaMqttTransport())
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}
