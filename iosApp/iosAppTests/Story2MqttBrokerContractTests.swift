import CocoaMQTT
import Foundation
import Shared
import XCTest
@testable import iosApp

/// STORY-2: the Swift CocoaMQTT transport against the staging Mosquitto broker on the Mac (localhost:1883).
///
/// Swift never sees `IotManager`, so these tests drive the component that speaks MQTT 3.1.1 to the broker, the
/// `CocoaMqttTransport` behind the Kotlin `MqttTransport` protocol, and watch the broker with a second, independent
/// CocoaMQTT client. The Kotlin mapping from `publishString`/`subscribe` onto the transport (and re-subscribing after
/// a CONNACK) is covered by `Story2IosMqttClientManagerTest` in `core/data/src/iosTest`.
///
/// Each test uses its own topic prefix, so retained messages from other runs never interfere, and clears the retained
/// messages it publishes. When the broker is not running, every test fails (it does not skip) and says how to start it.
final class Story2MqttBrokerContractTests: XCTestCase {

    private static let brokerHost = "localhost"
    private static let brokerPort: UInt16 = 1883
    private static let timeout: TimeInterval = 10

    private var prefix = ""
    private var clients: [BrokerClient] = []
    private var transports: [CocoaMqttTransport] = []
    private var retainedTopics: [String] = []

    override func setUpWithError() throws {
        try super.setUpWithError()
        prefix = "story2-\(UUID().uuidString.prefix(8).lowercased())"
        // Fails the test with the start command when the broker is down.
        _ = try connectedBrokerClient(name: "probe")
    }

    override func tearDownWithError() throws {
        if !retainedTopics.isEmpty, let cleaner = try? connectedBrokerClient(name: "cleaner") {
            for topic in retainedTopics {
                cleaner.clearRetained(topic: topic)
            }
            _ = cleaner.waitForPublishAcks(count: retainedTopics.count, timeout: Self.timeout)
        }
        transports.forEach { $0.listener = nil }
        clients.forEach { $0.disconnect() }
        clients = []
        transports = []
        retainedTopics = []
        try super.tearDownWithError()
    }

    // AC9
    // GIVEN the transport connected to the staging broker and an observer subscribed to t/x
    // WHEN the transport publishes {"command":"water"} on t/x
    // THEN the observer receives exactly that payload, published at QoS 1
    func testStory2_publishedCommandReachesSubscriberWithExactPayloadAtQos1() throws {
        let topic = "\(prefix)/t/x"
        let payload = "{\"command\":\"water\"}"
        let observer = try connectedBrokerClient(name: "observer")
        // Subscribed at QoS 2, so the broker delivers at the QoS the transport published with.
        try observer.subscribeAndWait(topicFilter: topic, qos: .qos2, timeout: Self.timeout)
        let (transport, _) = try connectedTransport(clientId: "\(prefix)-ios")

        _ = transport.publish(topic: topic, payload: Data(payload.utf8))

        let message = try XCTUnwrap(
            observer.waitForMessage(timeout: Self.timeout) { $0.topic == topic },
            "The observer received nothing on \(topic)"
        )
        XCTAssertEqual(Data(message.payload), Data(payload.utf8))
        XCTAssertEqual(message.qos, .qos1)
    }

    // GIVEN the transport connected to the staging broker
    // WHEN it publishes a QoS 1 message
    // THEN publish returns a packet identifier, and the broker's PUBACK for exactly that identifier reaches Kotlin
    //      (the iOS client waits for it before the water button shows "Watering started")
    func testPublishReportsThePubackOfItsMessage() throws {
        let topic = "\(prefix)/t/ack"
        let (transport, listener) = try connectedTransport(clientId: "\(prefix)-ack")

        let messageId = transport.publish(topic: topic, payload: Data("{}".utf8))

        XCTAssertGreaterThan(messageId, 0)
        XCTAssertTrue(
            waitUntil(timeout: Self.timeout) { listener.publishAcks.contains(messageId) },
            "No PUBACK reported for message \(messageId); reported: \(listener.publishAcks)"
        )
    }

    // GIVEN a transport that never connected
    // WHEN it publishes
    // THEN publish returns -1, so Kotlin fails the command at once
    func testPublishBeforeAnyConnectIsRefused() {
        let transport = CocoaMqttTransport()

        XCTAssertEqual(transport.publish(topic: "\(prefix)/t/x", payload: Data("{}".utf8)), -1)
    }

    // AC10
    // GIVEN retained messages on devices/a/info and devices/b/info
    // WHEN the transport subscribes to devices/+/info
    // THEN both retained payloads arrive with their topics devices/a/info and devices/b/info
    func testStory2_wildcardSubscriptionReceivesBothRetainedInfoMessages() throws {
        let topicA = "\(prefix)/devices/a/info"
        let topicB = "\(prefix)/devices/b/info"
        let payloadA = "{\"name\":\"A\",\"deviceId\":\"a\",\"type\":\"plant\"}"
        let payloadB = "{\"name\":\"Bazilikas Šilas\",\"deviceId\":\"b\",\"type\":\"plant\"}"
        let publisher = try connectedBrokerClient(name: "publisher")
        retainedTopics += [topicA, topicB]
        publisher.publish(topic: topicA, payload: payloadA, retained: true)
        publisher.publish(topic: topicB, payload: payloadB, retained: true)
        XCTAssertTrue(
            publisher.waitForPublishAcks(count: 2, timeout: Self.timeout),
            "The broker did not acknowledge the retained messages"
        )
        let (transport, listener) = try connectedTransport(clientId: "\(prefix)-ios")

        transport.subscribe(topicFilter: "\(prefix)/devices/+/info")

        XCTAssertTrue(
            waitUntil(timeout: Self.timeout) { listener.messages.count >= 2 },
            "Expected 2 retained messages, got \(listener.messages.count)"
        )
        let received = Dictionary(uniqueKeysWithValues: listener.messages.map { ($0.topic, $0.payload) })
        XCTAssertEqual(received, [topicA: Data(payloadA.utf8), topicB: Data(payloadB.utf8)])
    }

    // AC12 (broker side; the Kotlin re-subscribe is Story2IosMqttClientManagerTest)
    // GIVEN the transport subscribed to devices/+/availability
    // WHEN the broker drops its connection (another client takes over its client ID)
    // THEN the transport reconnects on its own and reports a new CONNACK, and after SUBSCRIBE is sent again (what the
    //      Kotlin layer does on every CONNACK) a message published after the reconnect arrives
    func testStory2_transportReconnectsAfterDropAndResubscribeReceivesAgain() throws {
        let clientId = "\(prefix)-ios"
        let filter = "\(prefix)/devices/+/availability"
        let topic = "\(prefix)/devices/basil/availability"
        let publisher = try connectedBrokerClient(name: "publisher")
        let (transport, listener) = try connectedTransport(clientId: clientId)
        transport.subscribe(topicFilter: filter)
        XCTAssertTrue(
            publishUntilReceived(publisher: publisher, listener: listener, topic: topic, payload: "online"),
            "The subscription did not work before the drop"
        )

        let intruder = BrokerClient(clientId: clientId, host: Self.brokerHost, port: Self.brokerPort)
        try intruder.connect(timeout: Self.timeout)
        intruder.disconnect()

        XCTAssertTrue(
            waitUntil(timeout: Self.timeout) { listener.disconnectedCount >= 1 },
            "The transport did not report the dropped connection"
        )
        XCTAssertTrue(
            waitUntil(timeout: 20) { listener.connectedCount >= 2 },
            "The transport did not reconnect on its own with a new CONNACK"
        )
        transport.subscribe(topicFilter: filter)
        XCTAssertTrue(
            publishUntilReceived(publisher: publisher, listener: listener, topic: topic, payload: "offline"),
            "No message arrived after the reconnect and re-subscribe"
        )
    }

    // MARK: - Helpers

    private func connectedBrokerClient(name: String) throws -> BrokerClient {
        let client = BrokerClient(clientId: "\(prefix)-\(name)", host: Self.brokerHost, port: Self.brokerPort)
        clients.append(client)
        try client.connect(timeout: Self.timeout)
        return client
    }

    private func connectedTransport(clientId: String) throws -> (CocoaMqttTransport, RecordingTransportListener) {
        let transport = CocoaMqttTransport()
        let listener = RecordingTransportListener()
        transport.listener = listener
        transports.append(transport)
        transport.connect(host: Self.brokerHost, port: Int32(Self.brokerPort), clientId: clientId)
        guard waitUntil(timeout: Self.timeout, { listener.connectedCount >= 1 }) else {
            throw BrokerUnreachable.message(
                "CocoaMqttTransport did not report a CONNACK from \(Self.brokerHost):\(Self.brokerPort)"
            )
        }
        return (transport, listener)
    }

    /// Publishes (not retained) every 300 ms until the transport receives [payload] on [topic]. The transport has no
    /// SUBACK callback, so this avoids racing the SUBSCRIBE.
    private func publishUntilReceived(
        publisher: BrokerClient,
        listener: RecordingTransportListener,
        topic: String,
        payload: String
    ) -> Bool {
        let expected = Data(payload.utf8)
        let deadline = Date().addingTimeInterval(Self.timeout)
        var received = false
        while !received && Date() < deadline {
            publisher.publish(topic: topic, payload: payload, retained: false)
            received = waitUntil(timeout: 0.3) {
                listener.messages.contains { $0.topic == topic && $0.payload == expected }
            }
        }
        return received
    }
}

// MARK: - Test doubles and broker client

enum BrokerUnreachable: Error, CustomStringConvertible {
    case message(String)

    var description: String {
        switch self {
        case .message(let text): return text
        }
    }
}

/// Spins the main run loop (CocoaMQTT calls back on the main queue) until [condition] holds or [timeout] passes.
func waitUntil(timeout: TimeInterval, _ condition: () -> Bool) -> Bool {
    let deadline = Date().addingTimeInterval(timeout)
    while !condition() && Date() < deadline {
        RunLoop.main.run(until: Date().addingTimeInterval(0.02))
    }
    return condition()
}

/// Records what the transport reports to the Kotlin side.
final class RecordingTransportListener: NSObject, MqttTransportListener {
    struct Message {
        let topic: String
        let payload: Data
    }

    private let lock = NSLock()
    private var _connecting = 0
    private var _connected = 0
    private var _disconnected = 0
    private var _messages: [Message] = []
    private var _publishAcks: [Int32] = []

    var connectedCount: Int { lock.withLock { _connected } }
    var disconnectedCount: Int { lock.withLock { _disconnected } }
    var messages: [Message] { lock.withLock { _messages } }
    var publishAcks: [Int32] { lock.withLock { _publishAcks } }

    func onConnecting() { lock.withLock { _connecting += 1 } }
    func onConnected() { lock.withLock { _connected += 1 } }
    func onDisconnected() { lock.withLock { _disconnected += 1 } }
    func onMessage(topic: String, payload: Data) {
        lock.withLock { _messages.append(Message(topic: topic, payload: payload)) }
    }
    func onPublishAcknowledged(messageId: Int32) { lock.withLock { _publishAcks.append(messageId) } }
}

/// An independent MQTT 3.1.1 client that plays the other side of the broker: observer, publisher or intruder.
final class BrokerClient {
    private let mqtt: CocoaMQTT
    private var connected = false
    private var subscribed: Set<String> = []
    private var publishAcks = 0
    private var received: [CocoaMQTTMessage] = []

    init(clientId: String, host: String, port: UInt16) {
        mqtt = CocoaMQTT(clientID: clientId, host: host, port: port)
        mqtt.cleanSession = true
        mqtt.autoReconnect = false
        mqtt.didConnectAck = { [weak self] _, ack in self?.connected = (ack == .accept) }
        mqtt.didSubscribeTopics = { [weak self] _, success, _ in
            success.allKeys.compactMap { $0 as? String }.forEach { self?.subscribed.insert($0) }
        }
        mqtt.didPublishAck = { [weak self] _, _ in self?.publishAcks += 1 }
        mqtt.didReceiveMessage = { [weak self] _, message, _ in self?.received.append(message) }
        mqtt.didDisconnect = { [weak self] _, _ in self?.connected = false }
    }

    func connect(timeout: TimeInterval) throws {
        _ = mqtt.connect()
        guard waitUntil(timeout: timeout, { connected }) else {
            XCTFail(Self.unreachableMessage)
            throw BrokerUnreachable.message(Self.unreachableMessage)
        }
    }

    func subscribeAndWait(topicFilter: String, qos: CocoaMQTTQoS, timeout: TimeInterval) throws {
        mqtt.subscribe(topicFilter, qos: qos)
        guard waitUntil(timeout: timeout, { subscribed.contains(topicFilter) }) else {
            throw BrokerUnreachable.message("The broker did not acknowledge SUBSCRIBE \(topicFilter)")
        }
    }

    func publish(topic: String, payload: String, retained: Bool) {
        mqtt.publish(CocoaMQTTMessage(topic: topic, string: payload, qos: .qos1, retained: retained))
    }

    /// An empty retained PUBLISH deletes the retained message of [topic].
    func clearRetained(topic: String) {
        mqtt.publish(CocoaMQTTMessage(topic: topic, payload: [], qos: .qos1, retained: true))
    }

    /// Waits until this client has received [count] PUBACKs in total since it connected.
    func waitForPublishAcks(count: Int, timeout: TimeInterval) -> Bool {
        waitUntil(timeout: timeout) { publishAcks >= count }
    }

    func waitForMessage(timeout: TimeInterval, where matches: (CocoaMQTTMessage) -> Bool) -> CocoaMQTTMessage? {
        _ = waitUntil(timeout: timeout) { received.contains(where: matches) }
        return received.first(where: matches)
    }

    func disconnect() {
        mqtt.disconnect()
    }

    static let unreachableMessage = """
        The staging Mosquitto broker is not reachable on localhost:1883. Start it with \
        `docker compose up -d --build` in the pliuskis-staging project (see staging/README.md), \
        check it with `docker compose ps`, then run the tests again.
        """
}
