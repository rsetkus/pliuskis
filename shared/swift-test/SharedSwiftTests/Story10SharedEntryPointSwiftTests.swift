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

        try SharedEntryPointKt.doInitShared(settings: settings)
        let viewModel = SharedEntryPointKt.createDevicesViewModel()

        XCTAssertTrue(viewModel.devices.value is DevicesListScreenStateLoading)
    }
}
