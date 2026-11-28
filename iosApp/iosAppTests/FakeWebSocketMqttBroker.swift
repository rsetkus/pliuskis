import Foundation
import Network

/// A fake MQTT 3.1.1 broker over WebSocket inside the test process (Network.framework), so the WebSocket transport's
/// tests need no broker outside the test. It listens on a free localhost port, accepts any request path and query
/// (AWS IoT checks the SigV4 signature; this fake doesn't), answers CONNECT with CONNACK 0, SUBSCRIBE with SUBACK
/// QoS 1, PUBLISH QoS 1 with PUBACK, PINGREQ with PINGRESP, and records every CONNECT's client ID. `restart()` closes
/// every connection and listens again on the same port, as a broker restart does; `publish` sends a QoS 0 PUBLISH to
/// every client whose subscription matches.
final class FakeWebSocketMqttBroker {
    private let queue = DispatchQueue(label: "lt.setkus.pliuskis.tests.fake-broker")
    private var listener: NWListener?
    private var clients: [ObjectIdentifier: Client] = [:]
    private var connectedClientIds: [String] = []
    private(set) var port: UInt16 = 0

    /// The client ID of every CONNECT, in order.
    var connects: [String] { queue.sync { connectedClientIds } }

    /// Whether a connected client has subscribed to `filter` now.
    func hasSubscriber(_ filter: String) -> Bool {
        queue.sync { clients.values.contains { $0.filters.contains(filter) } }
    }

    func start() throws {
        let webSocket = NWProtocolWebSocket.Options()
        webSocket.autoReplyPing = true
        webSocket.setClientRequestHandler(queue) { subprotocols, _ in
            NWProtocolWebSocket.Response(
                status: .accept,
                subprotocol: subprotocols.contains("mqtt") ? "mqtt" : nil
            )
        }
        let parameters = NWParameters.tcp
        parameters.allowLocalEndpointReuse = true
        parameters.defaultProtocolStack.applicationProtocols.insert(webSocket, at: 0)
        let listener = port == 0
            ? try NWListener(using: parameters)
            : try NWListener(using: parameters, on: NWEndpoint.Port(rawValue: port) ?? .any)
        let ready = DispatchSemaphore(value: 0)
        listener.stateUpdateHandler = { state in
            if case .ready = state { ready.signal() }
        }
        listener.newConnectionHandler = { [weak self] connection in self?.accept(connection) }
        listener.start(queue: queue)
        _ = ready.wait(timeout: .now() + 5)
        port = listener.port?.rawValue ?? 0
        self.listener = listener
    }

    func stop() {
        queue.sync {
            listener?.cancel()
            listener = nil
            clients.values.forEach { $0.connection.cancel() }
            clients = [:]
        }
    }

    /// A broker restart: every connection closes, and the broker listens again on the same port.
    func restart() throws {
        stop()
        try start()
    }

    func publish(topic: String, payload: String) {
        queue.async {
            let packet = Self.packet(type: 0x30, body: Self.string(topic) + Data(payload.utf8))
            self.clients.values
                .filter { client in client.filters.contains { Self.topic(topic, matches: $0) } }
                .forEach { $0.send(packet) }
        }
    }

    // MARK: - Connections (on `queue`)

    private final class Client {
        let connection: NWConnection
        var buffer = Data()
        var filters: Set<String> = []

        init(connection: NWConnection) {
            self.connection = connection
        }

        func send(_ packet: Data) {
            let metadata = NWProtocolWebSocket.Metadata(opcode: .binary)
            let context = NWConnection.ContentContext(identifier: "mqtt", metadata: [metadata])
            connection.send(content: packet, contentContext: context, isComplete: true, completion: .idempotent)
        }
    }

    private func accept(_ connection: NWConnection) {
        let client = Client(connection: connection)
        clients[ObjectIdentifier(connection)] = client
        connection.stateUpdateHandler = { [weak self] state in
            switch state {
            case .failed, .cancelled:
                self?.clients[ObjectIdentifier(connection)] = nil
            default:
                break
            }
        }
        connection.start(queue: queue)
        receive(client)
    }

    private func receive(_ client: Client) {
        client.connection.receiveMessage { [weak self] data, _, _, error in
            guard let self, error == nil else {
                client.connection.cancel()
                return
            }
            if let data { client.buffer.append(data) }
            self.handlePackets(of: client)
            if self.clients[ObjectIdentifier(client.connection)] != nil { self.receive(client) }
        }
    }

    private func handlePackets(of client: Client) {
        while let (type, body, length) = Self.nextPacket(in: client.buffer) {
            client.buffer.removeFirst(length)
            handle(type: type, body: body, from: client)
        }
    }

    private func handle(type: UInt8, body: Data, from client: Client) {
        switch type >> 4 {
        case 1: // CONNECT: protocol name, level, flags, keep-alive, then the client ID
            let nameLength = Self.uint16(body, at: 0)
            let clientIdAt = 2 + nameLength + 4
            connectedClientIds.append(Self.readString(body, at: clientIdAt))
            client.send(Data([0x20, 0x02, 0x00, 0x00]))
        case 8: // SUBSCRIBE: packet ID, then (filter, QoS) pairs
            var at = 2
            var granted = Data()
            while at < body.count {
                let filter = Self.readString(body, at: at)
                client.filters.insert(filter)
                at += 2 + Self.uint16(body, at: at) + 1
                granted.append(0x01)
            }
            client.send(Self.packet(type: 0x90, body: body.prefix(2) + granted))
        case 10: // UNSUBSCRIBE
            client.filters.remove(Self.readString(body, at: 2))
            client.send(Self.packet(type: 0xB0, body: body.prefix(2)))
        case 3: // PUBLISH from the client: answer PUBACK for QoS 1
            if (type >> 1) & 0x03 == 1 {
                let packetIdAt = 2 + Self.uint16(body, at: 0)
                client.send(Self.packet(type: 0x40, body: body.subdata(in: packetIdAt..<packetIdAt + 2)))
            }
        case 12: // PINGREQ
            client.send(Data([0xD0, 0x00]))
        case 14: // DISCONNECT
            client.connection.cancel()
        default:
            break
        }
    }

    // MARK: - MQTT encoding

    /// The next whole packet in `buffer`: its first byte, its body and its total length; nil while incomplete.
    private static func nextPacket(in buffer: Data) -> (UInt8, Data, Int)? {
        let bytes = [UInt8](buffer)
        guard bytes.count >= 2 else { return nil }
        var remaining = 0
        var multiplier = 1
        var index = 1
        var lengthDone = false
        while !lengthDone && index < bytes.count && index <= 4 {
            remaining += Int(bytes[index] & 0x7F) * multiplier
            multiplier *= 128
            lengthDone = bytes[index] & 0x80 == 0
            index += 1
        }
        let total = index + remaining
        guard lengthDone, bytes.count >= total else { return nil }
        return (bytes[0], Data(bytes[index..<total]), total)
    }

    private static func packet(type: UInt8, body: Data) -> Data {
        var length = body.count
        var encoded = Data([type])
        repeat {
            var byte = UInt8(length % 128)
            length /= 128
            if length > 0 { byte |= 0x80 }
            encoded.append(byte)
        } while length > 0
        return encoded + body
    }

    private static func string(_ text: String) -> Data {
        let utf8 = Data(text.utf8)
        return Data([UInt8(utf8.count >> 8), UInt8(utf8.count & 0xFF)]) + utf8
    }

    private static func uint16(_ data: Data, at offset: Int) -> Int {
        let bytes = [UInt8](data)
        return Int(bytes[offset]) << 8 | Int(bytes[offset + 1])
    }

    private static func readString(_ data: Data, at offset: Int) -> String {
        let length = uint16(data, at: offset)
        let bytes = [UInt8](data)
        return String(decoding: bytes[(offset + 2)..<(offset + 2 + length)], as: UTF8.self)
    }

    private static func topic(_ topic: String, matches filter: String) -> Bool {
        let topicLevels = topic.split(separator: "/", omittingEmptySubsequences: false)
        let filterLevels = filter.split(separator: "/", omittingEmptySubsequences: false)
        if filterLevels.last == "#" {
            let fixed = filterLevels.dropLast()
            return topicLevels.count >= fixed.count && zip(fixed, topicLevels).allSatisfy { $0 == "+" || $0 == $1 }
        }
        return topicLevels.count == filterLevels.count && zip(filterLevels, topicLevels).allSatisfy { $0 == "+" || $0 == $1 }
    }
}
