import CocoaMQTT
import CocoaMQTTWebSocket
import Foundation
import os
import Shared

/// The app's MQTT socket: CocoaMQTT (MQTT 3.1.1) behind the Kotlin `MqttTransport` protocol, passed to `initShared`.
///
/// It only moves packets and reports what happens; the `IotManager` contract (one connect attempt at a time, the
/// 10 s wait, re-subscribing after every CONNACK, topic matching, UTF-8) is in Kotlin. The session is clean and
/// CocoaMQTT reconnects on its own after a lost connection; every attempt, including an automatic one, is reported
/// as `onConnecting`, then `onConnected` (CONNACK accepted) or `onDisconnected`. A publish returns CocoaMQTT's packet
/// identifier (-1 when it refuses the message) and its PUBACK is reported as `onPublishAcknowledged`; Kotlin waits for
/// it, because CocoaMQTT also queues a QoS 1 message while disconnected. CocoaMQTT calls back on the main queue.
///
/// `init()` connects over plain TCP to the host and port of `connect` (the LAN broker). `init(webSocketUrlProvider:)`
/// connects with MQTT over WebSocket instead, to the URL the provider gives for each attempt (AWS IoT's signed
/// `wss://<endpoint>/mqtt`); CocoaMQTT's automatic reconnects ask the provider again, so every attempt gets a new URL.
///
/// An attempt the broker answered but refused is reported as `onConnectionRefused` instead of `onDisconnected`: a
/// CONNACK with a refusal code, or, over WebSocket, an upgrade answered with an HTTP error (AWS IoT: 403, which URLSession
/// reports as a bad server response) or a WebSocket that opened and closed before any CONNACK (AWS IoT: an identity
/// without an AWS IoT policy). A socket that never opened (no network, DNS failure) is `onDisconnected`. `disconnect()`
/// closes the connection and turns the automatic reconnect off until the next `connect`.
final class CocoaMqttTransport: NSObject, MqttTransport {
    private static let log = Logger(subsystem: "lt.setkus.pliuskis", category: "mqtt")

    var listener: MqttTransportListener?

    private let webSocketUrlProvider: MqttWebSocketUrlProvider?
    private let webSocketAttempts = WebSocketAttemptState()
    private var mqtt: CocoaMQTT?
    /// Main queue only: whether the current attempt got an accepted CONNACK, or a refusing one.
    private var connAckAccepted = false
    private var connAckRefused = false

    /// MQTT over plain TCP.
    override init() {
        webSocketUrlProvider = nil
        super.init()
    }

    /// MQTT over WebSocket, to the URL `webSocketUrlProvider` gives for each connect attempt.
    init(webSocketUrlProvider: MqttWebSocketUrlProvider) {
        self.webSocketUrlProvider = webSocketUrlProvider
        super.init()
    }

    func connect(host: String, port: Int32, clientId: String) {
        let client = client(host: host, port: UInt16(clamping: port), clientId: clientId)
        Self.log.info("MQTT connecting to \(host, privacy: .public):\(port) as \(clientId, privacy: .public)")
        client.autoReconnect = true
        if !client.connect() {
            Self.log.error("MQTT connect could not start")
            listener?.onDisconnected()
        }
    }

    func disconnect() {
        guard let client = mqtt else { return }
        Self.log.info("MQTT closing the connection; no automatic reconnect")
        client.autoReconnect = false
        client.disconnect()
    }

    func subscribe(topicFilter: String) {
        mqtt?.subscribe(topicFilter, qos: .qos1)
    }

    func unsubscribe(topicFilter: String) {
        mqtt?.unsubscribe(topicFilter)
    }

    func publish(topic: String, payload: Data) -> Int32 {
        let message = CocoaMQTTMessage(topic: topic, payload: [UInt8](payload), qos: .qos1, retained: false)
        let messageId = mqtt?.publish(message) ?? -1
        if messageId < 0 {
            Self.log.error("MQTT publish on \(topic, privacy: .public) refused")
        }
        return Int32(clamping: messageId)
    }

    /// The one CocoaMQTT client of this transport, created on the first connect, set to these settings.
    private func client(host: String, port: UInt16, clientId: String) -> CocoaMQTT {
        let client = mqtt ?? newClient(clientId: clientId)
        client.host = host
        client.port = port
        client.clientID = clientId
        mqtt = client
        return client
    }

    private func newClient(clientId: String) -> CocoaMQTT {
        let client = CocoaMQTT(clientID: clientId, socket: newSocket())
        client.cleanSession = true
        client.autoReconnect = true
        client.didChangeState = { [weak self] _, state in
            if state == .connecting {
                self?.connAckAccepted = false
                self?.connAckRefused = false
                self?.listener?.onConnecting()
            }
        }
        client.didConnectAck = { [weak self] _, ack in
            Self.log.info("MQTT CONNACK \(ack.description, privacy: .public)")
            self?.connAckAccepted = ack == .accept
            self?.connAckRefused = ack != .accept
            if ack == .accept {
                self?.listener?.onConnected()
            }
        }
        client.didDisconnect = { [weak self] _, error in
            guard let self else { return }
            // The error's text only; a failing URLSession request's URL is in its userInfo and is never logged.
            Self.log.info("MQTT disconnected: \(error?.localizedDescription ?? "no error", privacy: .public)")
            if self.attemptWasRefused(error: error) {
                Self.log.info("MQTT connection refused by the broker")
                self.listener?.onConnectionRefused()
            } else {
                self.listener?.onDisconnected()
            }
        }
        client.didReceiveMessage = { [weak self] _, message, _ in
            self?.listener?.onMessage(topic: message.topic, payload: Data(message.payload))
        }
        client.didPublishAck = { [weak self] _, messageId in
            self?.listener?.onPublishAcknowledged(messageId: Int32(messageId))
        }
        return client
    }

    /// Whether the attempt that just ended was answered and refused by the broker, rather than lost or never answered.
    private func attemptWasRefused(error: Error?) -> Bool {
        let answeredWithoutConnAck = !connAckAccepted && webSocketUrlProvider != nil &&
            (webSocketAttempts.webSocketDidOpen || Self.isRefusedUpgrade(error))
        return connAckRefused || answeredWithoutConnAck
    }

    /// URLSession reports a WebSocket upgrade answered with an HTTP error status (not 101) as a bad server response.
    private static func isRefusedUpgrade(_ error: Error?) -> Bool {
        let nsError = error as NSError?
        return nsError?.domain == NSURLErrorDomain && nsError?.code == NSURLErrorBadServerResponse
    }

    /// Plain TCP, or a WebSocket whose every connection asks the provider for its URL.
    private func newSocket() -> CocoaMQTTSocketProtocol {
        let socket: CocoaMQTTSocketProtocol
        if let provider = webSocketUrlProvider {
            socket = CocoaMQTTWebSocket(
                builder: PerAttemptWebSocketConnectionBuilder(provider: provider, attempts: webSocketAttempts)
            )
        } else {
            socket = CocoaMQTTSocket()
        }
        return socket
    }
}
