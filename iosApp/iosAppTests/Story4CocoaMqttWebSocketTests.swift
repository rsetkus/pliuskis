import Darwin
import Foundation
import Shared
import XCTest
@testable import iosApp

/// STORY-4 (PLIUS-37): `CocoaMqttTransport` over MQTT over WebSocket asks its URL provider for the URL of every
/// connect attempt, CocoaMQTT's own automatic reconnects included, so each attempt can use a newly signed URL.
///
/// No broker and no AWS: the provider points at a localhost port that nothing listens on, so every attempt fails and
/// CocoaMQTT retries on its own (after 1 s, then 2 s, ...). The checks against AWS IoT are manual and live in the
/// story's implementation map.
final class Story4CocoaMqttWebSocketTests: XCTestCase {

    private static let timeout: TimeInterval = 10

    private var transports: [CocoaMqttTransport] = []

    override func tearDownWithError() throws {
        transports.forEach { $0.listener = nil }
        transports = []
        try super.tearDownWithError()
    }

    // AC84
    // GIVEN a CocoaMqttTransport set to connect over WebSocket with a provider that returns
    //       ws://localhost:<a port with no listener>/mqtt and counts its calls
    // WHEN it connects, the attempt fails, and CocoaMQTT then retries on its own once (that attempt fails too)
    // THEN the provider has been called exactly 2 times, once per attempt
    func testStory4_webSocketUrlProviderIsAskedOncePerConnectAttemptIncludingTheAutomaticRetry() throws {
        let port = try Self.portWithNoListener()
        let provider = CountingWebSocketUrlProvider(url: try XCTUnwrap(URL(string: "ws://localhost:\(port)/mqtt")))
        let transport = CocoaMqttTransport(webSocketUrlProvider: provider)
        let listener = Story4TransportListener()
        transport.listener = listener
        transports.append(transport)

        transport.connect(host: "localhost", port: Int32(port), clientId: "story4-\(UUID().uuidString.prefix(8))")

        XCTAssertTrue(
            waitUntil(timeout: Self.timeout) { listener.disconnectedCount >= 2 },
            "Expected the first attempt and one automatic retry to fail; failed attempts: \(listener.disconnectedCount)"
        )
        // CocoaMQTT waits at least 2 s before the third attempt, so the count is read before that one starts.
        XCTAssertEqual(provider.callCount, 2, "The provider must be asked once per connect attempt")
    }

    /// A localhost TCP port that was free a moment ago: bound to port 0, read back, closed.
    private static func portWithNoListener() throws -> UInt16 {
        let socketFd = socket(AF_INET, SOCK_STREAM, 0)
        guard socketFd >= 0 else { throw Story4PortError.noSocket }
        defer { close(socketFd) }
        var address = sockaddr_in()
        address.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        address.sin_family = sa_family_t(AF_INET)
        address.sin_port = 0
        address.sin_addr.s_addr = inet_addr("127.0.0.1")
        var length = socklen_t(MemoryLayout<sockaddr_in>.size)
        let bound = withUnsafeMutablePointer(to: &address) { pointer in
            pointer.withMemoryRebound(to: sockaddr.self, capacity: 1) { socketAddress in
                bind(socketFd, socketAddress, length) == 0 && getsockname(socketFd, socketAddress, &length) == 0
            }
        }
        guard bound else { throw Story4PortError.noFreePort }
        return UInt16(bigEndian: address.sin_port)
    }
}

private enum Story4PortError: Error {
    case noSocket
    case noFreePort
}

/// The test's URL provider: always the same URL, and it counts how often the transport asked for one.
private final class CountingWebSocketUrlProvider: MqttWebSocketUrlProvider {
    private let lock = NSLock()
    private let url: URL
    private var calls = 0

    init(url: URL) {
        self.url = url
    }

    var callCount: Int { lock.withLock { calls } }

    func webSocketUrl() throws -> URL {
        lock.withLock { calls += 1 }
        return url
    }
}

/// Counts the failed or lost connections the transport reports to Kotlin.
private final class Story4TransportListener: NSObject, MqttTransportListener {
    private let lock = NSLock()
    private var disconnected = 0
    private var refused = 0

    var disconnectedCount: Int { lock.withLock { disconnected } }

    func onConnecting() {}
    func onConnected() {}
    func onDisconnected() { lock.withLock { disconnected += 1 } }
    func onConnectionRefused() { lock.withLock { refused += 1 } }
    func onMessage(topic: String, payload: Data) {}
    func onPublishAcknowledged(messageId: Int32) {}
}
