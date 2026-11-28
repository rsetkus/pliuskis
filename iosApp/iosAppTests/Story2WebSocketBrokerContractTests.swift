import CocoaMQTT
import Foundation
import Shared
import XCTest
@testable import iosApp

/// STORY-2 AC50 on iOS: the release transport, `CocoaMqttTransport` over MQTT over WebSocket, against
/// `FakeWebSocketMqttBroker`, a WebSocket MQTT broker inside the test process (no broker outside the test). The fake
/// accepts any query, so the provider gives a URL with an `X-Amz-Date` that changes on every attempt, like a newly
/// signed AWS IoT URL.
///
/// Swift never sees `IotManager`; the Kotlin contract over this transport (re-subscribing after every CONNACK,
/// `reconnects()` once per reconnect, a `connect()` joining a running reconnect) is `Story2IosAwsIotClientTest` and
/// `Story2IosMqttClientManagerTest` in `core/data/src/iosTest`. Here the test sends SUBSCRIBE again after the new
/// CONNACK, as the Kotlin client does.
final class Story2WebSocketBrokerContractTests: XCTestCase {

    private static let timeout: TimeInterval = 10

    private var broker: FakeWebSocketMqttBroker?
    private var transports: [CocoaMqttTransport] = []

    override func tearDownWithError() throws {
        transports.forEach { $0.listener = nil }
        transports.forEach { $0.disconnect() }
        transports = []
        broker?.stop()
        broker = nil
        try super.tearDownWithError()
    }

    // AC50 (iOS)
    // GIVEN the WebSocket transport connected to the fake broker and subscribed to devices/+/info
    // WHEN the broker restarts
    // THEN the transport reconnects on its own with a new URL, with exactly one new CONNECT and one new CONNACK, and
    //      after SUBSCRIBE is sent again a message published after the restart arrives
    func testStory2_webSocketTransportReconnectsWithANewUrlAndReceivesAgain() throws {
        let broker = FakeWebSocketMqttBroker()
        try broker.start()
        self.broker = broker
        let filter = "devices/+/info"
        let provider = DatedWebSocketUrlProvider(port: broker.port)
        let transport = CocoaMqttTransport(webSocketUrlProvider: provider)
        let listener = RecordingTransportListener()
        transport.listener = listener
        transports.append(transport)
        transport.connect(host: "localhost", port: Int32(broker.port), clientId: "story2ws-ios")
        XCTAssertTrue(waitUntil(timeout: Self.timeout) { listener.connectedCount == 1 }, "No CONNACK from the fake broker")
        transport.subscribe(topicFilter: filter)
        XCTAssertTrue(waitUntil(timeout: Self.timeout) { broker.hasSubscriber(filter) }, "No SUBSCRIBE reached the broker")
        broker.publish(topic: "devices/a/info", payload: "before")
        XCTAssertTrue(
            waitUntil(timeout: Self.timeout) { listener.messages.contains { $0.topic == "devices/a/info" } },
            "The subscription did not work before the restart"
        )

        try broker.restart()

        XCTAssertTrue(
            waitUntil(timeout: 20) { listener.connectedCount >= 2 },
            "The WebSocket transport did not reconnect on its own with a new CONNACK"
        )
        transport.subscribe(topicFilter: filter)
        XCTAssertTrue(waitUntil(timeout: Self.timeout) { broker.hasSubscriber(filter) }, "No SUBSCRIBE after the restart")
        broker.publish(topic: "devices/b/info", payload: "after")
        XCTAssertTrue(
            waitUntil(timeout: Self.timeout) {
                listener.messages.contains { $0.topic == "devices/b/info" && $0.payload == Data("after".utf8) }
            },
            "No message arrived after the restart and re-subscribe"
        )
        XCTAssertEqual(listener.connectedCount, 2, "Exactly one new CONNACK for the one restart")
        XCTAssertEqual(broker.connects, ["story2ws-ios", "story2ws-ios"], "Exactly one new CONNECT for the restart")
        let urls = provider.givenUrls
        XCTAssertGreaterThanOrEqual(urls.count, 2)
        XCTAssertEqual(Set(urls).count, urls.count, "Every attempt must open its own URL")
    }
}

/// Gives ws://localhost:<port>/mqtt with a new X-Amz-Date on every call, like a URL signed at each attempt, and keeps
/// the URLs it gave.
private final class DatedWebSocketUrlProvider: MqttWebSocketUrlProvider {
    private let lock = NSLock()
    private let port: UInt16
    private var urls: [URL] = []

    init(port: UInt16) {
        self.port = port
    }

    var givenUrls: [URL] { lock.withLock { urls } }

    func webSocketUrl() throws -> URL {
        try lock.withLock {
            let stamp = String(format: "20261121T12%02d00Z", urls.count + 1)
            let url = try XCTUnwrap(URL(string: "ws://localhost:\(port)/mqtt?X-Amz-Date=\(stamp)"))
            urls.append(url)
            return url
        }
    }
}
