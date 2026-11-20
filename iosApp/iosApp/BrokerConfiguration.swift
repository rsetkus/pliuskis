import Foundation
import Shared

/// The broker settings the app passes to `initShared`. The host comes from `BROKER_HOST` of the active xcconfig,
/// which the build writes into the `BrokerHost` key of Info.plist; the app holds no host of its own. The connection is
/// plaintext MQTT on port 1883 (the LAN broker; no TLS on iOS yet).
enum BrokerConfiguration {
    static let infoPlistHostKey = "BrokerHost"
    static let plaintextPort: Int32 = 1883

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
}
