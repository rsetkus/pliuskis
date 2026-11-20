import Shared
import XCTest
@testable import iosApp

/// STORY-3, AC18: the broker settings the app passes to `initShared` come from `BROKER_HOST` of the active xcconfig,
/// which the build writes into the `BrokerHost` key of Info.plist. The builder reads that Info dictionary; the test
/// hands it the dictionary a build with `BROKER_HOST = 192.168.1.20` produces.
final class Story3BrokerSettingsTests: XCTestCase {

    private static let lanBrokerInfo: [String: Any] = ["BrokerHost": "192.168.1.20"]

    func testStory3_brokerHostFromXcconfigBecomesTheSettingsHost() {
        let settings = BrokerConfiguration.settings(infoDictionary: Self.lanBrokerInfo, clientId: "story3client")

        XCTAssertEqual(settings.host, "192.168.1.20")
    }

    func testStory3_settingsUsePlaintextPort1883() {
        let settings = BrokerConfiguration.settings(infoDictionary: Self.lanBrokerInfo, clientId: "story3client")

        XCTAssertEqual(settings.port, 1883)
    }

    func testStory3_settingsUseNoTls() {
        let settings = BrokerConfiguration.settings(infoDictionary: Self.lanBrokerInfo, clientId: "story3client")

        XCTAssertEqual(settings.tlsMode, TlsMode.none)
    }

    /// AC19 on the Swift side: the settings carry the per-install client ID they are given (the app passes
    /// `InstallClientIdKt.installClientId()`), not a new ID per launch.
    func testStory3_settingsCarryTheGivenInstallClientId() {
        let settings = BrokerConfiguration.settings(infoDictionary: Self.lanBrokerInfo, clientId: "story3client")

        XCTAssertEqual(settings.clientId, "story3client")
    }
}
