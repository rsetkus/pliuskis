import Foundation
import Shared

/// The opt-in to AWS IoT over Cognito for a Debug build (the dev stack), for checks against AWS IoT. Release builds
/// always connect to AWS IoT (`BrokerConfiguration`, `MqttConnection` = `awsIot`); without the argument a Debug build
/// uses the LAN broker (`BROKER_HOST`, plaintext, port 1883).
///
/// Launch arguments (the argument domain of the standard UserDefaults):
/// - `-awsIotCheck YES`: connect to this configuration's AWS IoT endpoint (`AMAZON_IOT_ENDPOINT`: the dev stack for
///   Debug, the prod stack for Release) with MQTT over WebSocket on port 443, a SigV4-signed URL from the signed-in
///   identity's temporary credentials (identity pool `COGNITO_IDENTITY_POOL_ID` of the same stack) and the client ID
///   `<identity ID>-<install suffix>`.
/// - `-awsIotTokenPlacement signed`: the session token inside the signed query; `appended` (the default) adds it after
///   signing.
enum AwsIotCheck {
    static let enabledKey = "awsIotCheck"
    static let tokenPlacementKey = "awsIotTokenPlacement"

    static func isEnabled(_ defaults: UserDefaults = .standard) -> Bool {
        defaults.bool(forKey: enabledKey)
    }

    static func tokenPlacement(_ defaults: UserDefaults = .standard) -> SecurityTokenPlacement {
        defaults.string(forKey: tokenPlacementKey) == "signed" ? .signed : .appended
    }
}
