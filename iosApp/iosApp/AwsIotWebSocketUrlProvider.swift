import Foundation
import Shared

/// The WebSocket URL of every AWS IoT connect attempt: newly signed by the shared code with the signed-in identity's
/// cached Cognito credentials (`signedAwsIotWebSocketUrl`), without waiting for the network. It throws when no fresh
/// credentials are cached; the shared code is then refreshing them, and CocoaMQTT's next automatic reconnect asks
/// again. The URL carries the signature and the session token, so nothing here logs it.
final class AwsIotWebSocketUrlProvider: MqttWebSocketUrlProvider {
    enum Failure: Error {
        case notAUrl
    }

    func webSocketUrl() throws -> URL {
        let signed = try SharedEntryPointKt.signedAwsIotWebSocketUrl()
        guard let url = URL(string: signed) else { throw Failure.notAUrl }
        return url
    }
}
