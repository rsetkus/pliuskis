import CocoaMQTT
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
final class CocoaMqttTransport: NSObject, MqttTransport {
    private static let log = Logger(subsystem: "lt.setkus.pliuskis", category: "mqtt")

    var listener: MqttTransportListener?

    private var mqtt: CocoaMQTT?

    func connect(host: String, port: Int32, clientId: String) {
        let client = client(host: host, port: UInt16(clamping: port), clientId: clientId)
        Self.log.info("MQTT connecting to \(host, privacy: .public):\(port) as \(clientId, privacy: .public)")
        if !client.connect() {
            Self.log.error("MQTT connect could not start")
            listener?.onDisconnected()
        }
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
        let client = CocoaMQTT(clientID: clientId)
        client.cleanSession = true
        client.autoReconnect = true
        client.didChangeState = { [weak self] _, state in
            if state == .connecting {
                self?.listener?.onConnecting()
            }
        }
        client.didConnectAck = { [weak self] _, ack in
            Self.log.info("MQTT CONNACK \(ack.description, privacy: .public)")
            if ack == .accept {
                self?.listener?.onConnected()
            }
        }
        client.didDisconnect = { [weak self] _, error in
            Self.log.info("MQTT disconnected: \(error?.localizedDescription ?? "no error", privacy: .public)")
            self?.listener?.onDisconnected()
        }
        client.didReceiveMessage = { [weak self] _, message, _ in
            self?.listener?.onMessage(topic: message.topic, payload: Data(message.payload))
        }
        client.didPublishAck = { [weak self] _, messageId in
            self?.listener?.onPublishAcknowledged(messageId: Int32(messageId))
        }
        return client
    }
}
