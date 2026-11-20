import Shared
import XCTest

/// STORY-10, AC50: Swift calls the `Shared` framework the way the iOS app will. `initShared` starts the Kotlin
/// graph, and the factory creates a `DevicesViewModel` without Swift touching Koin. Its first state is Loading.
///
/// Koin can be started only once per process, so this file has one test that calls `initShared`.
final class Story10SharedEntryPointSwiftTests: XCTestCase {

    @MainActor
    func testStory10_devicesViewModelFromFactoryStartsLoading() throws {
        let settings = MqttSettings(clientId: "story10swift", host: "localhost", port: 1883, tlsMode: TlsMode.none)

        try SharedEntryPointKt.doInitShared(settings: settings, transport: RefusingMqttTransport())
        let viewModel = SharedEntryPointKt.createDevicesViewModel()

        XCTAssertTrue(viewModel.devices.value is DevicesListScreenStateLoading)
    }
}

/// A broker that refuses every connection: each connect attempt ends at once with a disconnect.
private final class RefusingMqttTransport: NSObject, MqttTransport {
    var listener: MqttTransportListener?

    func connect(host: String, port: Int32, clientId: String) {
        listener?.onDisconnected()
    }

    func subscribe(topicFilter: String) {}

    func unsubscribe(topicFilter: String) {}

    func publish(topic: String, payload: Data) {}
}
