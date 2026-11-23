import CocoaMQTT
import CocoaMQTTWebSocket
import Foundation
import os

/// Gives the WebSocket URL of one MQTT connect attempt, for example a newly signed SigV4 URL of AWS IoT.
///
/// `CocoaMqttTransport(webSocketUrlProvider:)` asks for a URL on every attempt: the first connect and each of
/// CocoaMQTT's automatic reconnects, so no attempt reuses an older URL. CocoaMQTT asks synchronously while it opens
/// the socket, so the provider must answer at once and never wait for the network. A thrown error fails only that
/// attempt; CocoaMQTT's next automatic reconnect asks again.
protocol MqttWebSocketUrlProvider: AnyObject {
    func webSocketUrl() throws -> URL
}

/// CocoaMQTT's WebSocket connection builder for `CocoaMqttTransport`: it ignores the URL CocoaMQTT builds from the
/// host and port, asks the provider for this attempt's URL and opens CocoaMQTT's default connection to it (Apple's
/// `URLSessionWebSocketTask`, subprotocol `mqtt`).
///
/// It logs the host, port and path of each attempt, and its `X-Amz-Date` when it has one. It never logs the query
/// otherwise: a signed URL carries the signature, the credential scope and the session token there.
struct PerAttemptWebSocketConnectionBuilder: CocoaMQTTWebSocketConnectionBuilder {
    private static let log = Logger(subsystem: "lt.setkus.pliuskis", category: "mqtt")
    private static let signingDateKey = "X-Amz-Date"

    let provider: MqttWebSocketUrlProvider

    func buildConnection(forURL url: URL, withHeaders headers: [String: String]) throws -> CocoaMQTTWebSocketConnection {
        let attemptUrl: URL
        do {
            attemptUrl = try provider.webSocketUrl()
        } catch {
            Self.log.error("MQTT WebSocket URL unavailable: \(String(describing: type(of: error)), privacy: .public)")
            throw error
        }
        Self.logAttempt(attemptUrl)
        return try CocoaMQTTWebSocket.DefaultConnectionBuilder().buildConnection(forURL: attemptUrl, withHeaders: headers)
    }

    private static func logAttempt(_ url: URL) {
        let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        let host = components?.host ?? ""
        let port = components?.port ?? (components?.scheme == "wss" ? 443 : 80)
        let path = components?.path ?? ""
        let signingDate = components?.queryItems?.first { $0.name == signingDateKey }?.value ?? "none"
        // The names only, in URL order; the values (signature, credential, session token) are never logged.
        let parameterNames = (components?.queryItems ?? []).map(\.name).joined(separator: ",")
        log.info(
            "MQTT WebSocket attempt to \(host, privacy: .public):\(port) path \(path, privacy: .public) signed at \(signingDate, privacy: .public) query \(parameterNames, privacy: .public)"
        )
    }
}
