import Shared
import SwiftUI

/// The iOS app. It starts the shared (Kotlin) graph once, before the first screen asks for a ViewModel, with the
/// broker settings (host from the xcconfig, this install's client ID) and the CocoaMQTT socket the Kotlin MQTT client
/// runs on. Launched with `-awsIotCheck YES`, it connects to its stack's AWS IoT instead (`AwsIotCheck`): the dev stack
/// in Debug, the prod stack in Release.
@main
struct PliuskisApp: App {

    init() {
        if AwsIotCheck.isEnabled() {
            Self.startSharedOverAwsIot()
        } else {
            Self.startSharedOverLanBroker()
        }
    }

    private static func startSharedOverLanBroker() {
        let settings = BrokerConfiguration.settings(
            infoDictionary: Bundle.main.infoDictionary,
            clientId: InstallClientIdKt.installClientId()
        )
        SharedEntryPointKt.doInitShared(settings: settings, transport: CocoaMqttTransport())
    }

    /// MQTT over WebSocket to AWS IoT, every attempt with a newly signed URL.
    private static func startSharedOverAwsIot() {
        let settings = BrokerConfiguration.awsIotSettings(
            infoDictionary: Bundle.main.infoDictionary,
            clientId: InstallClientIdKt.installClientId()
        )
        SharedEntryPointKt.doInitSharedOverAwsIot(
            settings: settings,
            transport: CocoaMqttTransport(webSocketUrlProvider: AwsIotWebSocketUrlProvider()),
            tokenPlacement: AwsIotCheck.tokenPlacement()
        )
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}
