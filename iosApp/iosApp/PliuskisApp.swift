import Shared
import SwiftUI

/// The iOS app. It starts the shared (Kotlin) graph once, before the first screen asks for a ViewModel, with the
/// broker settings of this build (`BrokerConfiguration`) and the CocoaMQTT socket the Kotlin MQTT client runs on:
///
/// - Release: AWS IoT over Cognito, MQTT over a WebSocket on port 443 whose URL is newly signed for every attempt with
///   the signed-in identity's temporary credentials (the prod stack).
/// - Debug: the LAN broker (`BROKER_HOST`, plaintext, port 1883). Launched with `-awsIotCheck YES` (`AwsIotCheck`), a
///   Debug build connects to the dev stack's AWS IoT instead.
@main
struct PliuskisApp: App {

    init() {
        let clientId = InstallClientIdKt.installClientId()
        let info = Bundle.main.infoDictionary
        let settings = AwsIotCheck.isEnabled()
            ? BrokerConfiguration.awsIotSettings(infoDictionary: info, clientId: clientId)
            : BrokerConfiguration.settings(infoDictionary: info, clientId: clientId)
        if settings.tlsMode == TlsMode.websocketSigv4 {
            Self.startSharedOverAwsIot(settings: settings)
        } else {
            SharedEntryPointKt.doInitShared(settings: settings, transport: CocoaMqttTransport())
        }
    }

    /// MQTT over WebSocket to AWS IoT, every attempt with a newly signed URL.
    private static func startSharedOverAwsIot(settings: MqttSettings) {
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
