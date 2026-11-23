import Foundation
import Shared

/// The broker settings the app passes to `initShared`. The host comes from `BROKER_HOST` of the active xcconfig,
/// which the build writes into the `BrokerHost` key of Info.plist; the app holds no host of its own. The connection is
/// plaintext MQTT on port 1883 (the LAN broker; no TLS on iOS yet).
///
/// For AWS IoT over Cognito (`awsIotSettings`), the host is the AWS IoT endpoint of the `AmazonIotEndpoint` key
/// (`AMAZON_IOT_ENDPOINT`) and the port 443 (MQTT over WebSocket); the URL of each attempt is signed by the shared code.
enum BrokerConfiguration {
    static let infoPlistHostKey = "BrokerHost"
    static let infoPlistIotEndpointKey = "AmazonIotEndpoint"
    static let plaintextPort: Int32 = 1883
    static let webSocketTlsPort: Int32 = 443

    /// Settings for the broker named by `infoDictionary` (the app passes `Bundle.main.infoDictionary`), connecting
    /// as `clientId` (the app passes the per-install `InstallClientIdKt.installClientId()`). A missing host is an
    /// empty one: the connect then fails and the device list shows its error screen.
    static func settings(infoDictionary: [String: Any]?, clientId: String) -> MqttSettings {
        let host = infoDictionary?[infoPlistHostKey] as? String ?? ""
        return MqttSettings(
            clientId: clientId,
            host: host,
            port: plaintextPort,
            tlsMode: TlsMode.none
        )
    }

    /// Settings for AWS IoT over Cognito: the endpoint named by `infoDictionary` on port 443, and `clientId` (this
    /// install's client ID) as the suffix of the client ID `<identity ID>-<install suffix>`. A missing endpoint is an
    /// empty one: every connect then fails and the device list shows its error screen.
    static func awsIotSettings(infoDictionary: [String: Any]?, clientId: String) -> MqttSettings {
        let endpoint = infoDictionary?[infoPlistIotEndpointKey] as? String ?? ""
        return MqttSettings(
            clientId: clientId,
            host: endpoint,
            port: webSocketTlsPort,
            tlsMode: TlsMode.none
        )
    }
}
