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
///
/// It tells `attempts` when an attempt starts and when its WebSocket opens (the upgrade got HTTP 101), so the
/// transport can tell AWS IoT's refusal (the connection closed after it opened, before any CONNACK) from a network
/// error (it never opened).
struct PerAttemptWebSocketConnectionBuilder: CocoaMQTTWebSocketConnectionBuilder {
    private static let log = Logger(subsystem: "lt.setkus.pliuskis", category: "mqtt")
    private static let signingDateKey = "X-Amz-Date"

    let provider: MqttWebSocketUrlProvider
    let attempts: WebSocketAttemptState

    func buildConnection(forURL url: URL, withHeaders headers: [String: String]) throws -> CocoaMQTTWebSocketConnection {
        attempts.attemptStarted()
        let attemptUrl: URL
        do {
            attemptUrl = try provider.webSocketUrl()
        } catch {
            Self.log.error("MQTT WebSocket URL unavailable: \(String(describing: type(of: error)), privacy: .public)")
            throw error
        }
        Self.logAttempt(attemptUrl)
        let connection = try CocoaMQTTWebSocket.DefaultConnectionBuilder()
            .buildConnection(forURL: attemptUrl, withHeaders: headers)
        return OpenObservingConnection(inner: connection, onOpened: attempts.webSocketOpened)
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

/// Whether the WebSocket of the current connect attempt opened. CocoaMQTT builds and opens connections on its own
/// queue and reports the outcome on the main queue, so the flag is guarded by a lock.
final class WebSocketAttemptState {
    private let lock = NSLock()
    private var opened = false

    var webSocketDidOpen: Bool { lock.withLock { opened } }

    func attemptStarted() {
        lock.withLock { opened = false }
    }

    func webSocketOpened() {
        lock.withLock { opened = true }
    }
}

/// CocoaMQTT's WebSocket connection with one addition: it reports when the connection opened. Everything else is passed
/// through, with this object as the connection CocoaMQTT knows.
final class OpenObservingConnection: NSObject, CocoaMQTTWebSocketConnection, CocoaMQTTWebSocketConnectionDelegate {
    private let inner: CocoaMQTTWebSocketConnection
    private let onOpened: () -> Void

    weak var delegate: CocoaMQTTWebSocketConnectionDelegate?

    var queue: DispatchQueue {
        get { inner.queue }
        set { inner.queue = newValue }
    }

    init(inner: CocoaMQTTWebSocketConnection, onOpened: @escaping () -> Void) {
        self.inner = inner
        self.onOpened = onOpened
        super.init()
        inner.delegate = self
    }

    func connect() { inner.connect() }

    func disconnect() { inner.disconnect() }

    func write(data: Data, handler: @escaping (Error?) -> Void) { inner.write(data: data, handler: handler) }

    func connection(
        _ conn: CocoaMQTTWebSocketConnection,
        didReceive trust: SecTrust,
        completionHandler: @escaping (Bool) -> Void
    ) {
        delegate?.connection(self, didReceive: trust, completionHandler: completionHandler)
    }

    func urlSessionConnection(
        _ conn: CocoaMQTTWebSocketConnection,
        didReceiveTrust trust: SecTrust,
        didReceiveChallenge challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        delegate?.urlSessionConnection(
            self,
            didReceiveTrust: trust,
            didReceiveChallenge: challenge,
            completionHandler: completionHandler
        )
    }

    func connectionOpened(_ conn: CocoaMQTTWebSocketConnection) {
        onOpened()
        delegate?.connectionOpened(self)
    }

    func connectionClosed(_ conn: CocoaMQTTWebSocketConnection, withError error: Error?, withCode code: UInt16?) {
        delegate?.connectionClosed(self, withError: error, withCode: code)
    }

    func connection(_ conn: CocoaMQTTWebSocketConnection, receivedString string: String) {
        delegate?.connection(self, receivedString: string)
    }

    func connection(_ conn: CocoaMQTTWebSocketConnection, receivedData data: Data) {
        delegate?.connection(self, receivedData: data)
    }
}
