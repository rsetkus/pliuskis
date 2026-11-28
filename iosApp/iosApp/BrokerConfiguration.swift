import Foundation
import Shared

/// The broker settings the app passes to the shared code. The `MqttConnection` key of Info.plist (`MQTT_CONNECTION` of
/// the active xcconfig) picks the connection:
///
/// - `lan` (Debug): the LAN broker. The host comes from `BROKER_HOST` of the xcconfig (the `BrokerHost` key); the
///   connection is plaintext MQTT on port 1883.
/// - `awsIot` (Release): AWS IoT over Cognito. The host is the AWS IoT endpoint of the `AmazonIotEndpoint` key
///   (`AMAZON_IOT_ENDPOINT`) and the port 443: MQTT over a WebSocket whose URL the shared code signs with SigV4 for
///   every attempt, from the signed-in identity's temporary credentials.
///
/// The app holds no host of its own. A missing host or endpoint is an empty one: every connect then fails and the
/// device list shows its error screen.
enum BrokerConfiguration {
    static let infoPlistConnectionKey = "MqttConnection"
    static let infoPlistHostKey = "BrokerHost"
    static let infoPlistIotEndpointKey = "AmazonIotEndpoint"
    static let awsIotConnection = "awsIot"
    static let plaintextPort: Int32 = 1883
    static let webSocketTlsPort: Int32 = 443

    /// Settings for the connection named by `infoDictionary` (the app passes `Bundle.main.infoDictionary`),
    /// connecting as `clientId` (the app passes the per-install `InstallClientIdKt.installClientId()`; on AWS IoT it
    /// is the suffix of `<identity ID>-<install suffix>`).
    static func settings(infoDictionary: [String: Any]?, clientId: String) -> MqttSettings {
        let connection = infoDictionary?[infoPlistConnectionKey] as? String
        return connection == awsIotConnection
            ? awsIotSettings(infoDictionary: infoDictionary, clientId: clientId)
            : lanBrokerSettings(infoDictionary: infoDictionary, clientId: clientId)
    }

    /// The LAN broker: the host named by `infoDictionary`, plaintext MQTT on port 1883.
    static func lanBrokerSettings(infoDictionary: [String: Any]?, clientId: String) -> MqttSettings {
        let host = infoDictionary?[infoPlistHostKey] as? String ?? ""
        return MqttSettings(
            clientId: clientId,
            host: host,
            port: plaintextPort,
            tlsMode: TlsMode.none
        )
    }

    /// AWS IoT over Cognito: the endpoint named by `infoDictionary` on port 443, MQTT over a SigV4-signed WebSocket,
    /// and `clientId` (this install's client ID) as the suffix of the client ID `<identity ID>-<install suffix>`.
    static func awsIotSettings(infoDictionary: [String: Any]?, clientId: String) -> MqttSettings {
        let endpoint = infoDictionary?[infoPlistIotEndpointKey] as? String ?? ""
        return MqttSettings(
            clientId: clientId,
            host: endpoint,
            port: webSocketTlsPort,
            tlsMode: TlsMode.websocketSigv4
        )
    }
}
