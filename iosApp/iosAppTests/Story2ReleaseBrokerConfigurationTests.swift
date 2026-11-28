import Shared
import XCTest
@testable import iosApp

/// STORY-2 AC36 and AC52 (the Info.plist part): `BrokerConfiguration` gives a Release build the AWS IoT connection
/// over Cognito (MQTT over a SigV4-signed WebSocket to the AWS IoT endpoint on port 443) and a Debug build the LAN
/// broker (plaintext MQTT on port 1883). The test hands it the Info dictionary each configuration's build produces:
/// `MqttConnection` comes from `MQTT_CONNECTION` of the xcconfig (`awsIot` for Release, `lan` for Debug).
///
/// That the Kotlin client then fails a connect without an endpoint (and makes no Cognito call in Debug) is
/// `Story2IosAwsIotClientTest` in `core/data/src/iosTest`.
final class Story2ReleaseBrokerConfigurationTests: XCTestCase {

    private static let endpoint = "a1b2c3d4e5f6g7-ats.iot.eu-north-1.amazonaws.com"

    private static let releaseInfo: [String: Any] = [
        "MqttConnection": "awsIot",
        "BrokerHost": "localhost",
        "AmazonIotEndpoint": endpoint,
        "AwsRegion": "eu-north-1",
        "CognitoIdentityPoolId": "eu-north-1:00000000-0000-4000-8000-00000000test",
    ]

    private static let debugInfo: [String: Any] = [
        "MqttConnection": "lan",
        "BrokerHost": "localhost",
        "AmazonIotEndpoint": "example-dev-ats.iot.eu-north-1.amazonaws.com",
        "AwsRegion": "eu-north-1",
    ]

    // AC36
    // GIVEN the Release Info dictionary with IoT endpoint a1b2c3d4e5f6g7-ats.iot.eu-north-1.amazonaws.com and region
    //       eu-north-1
    // WHEN BrokerConfiguration builds the settings
    // THEN the connection target is wss://<endpoint>:443/mqtt with SigV4 signing
    func testStory2_releaseSettingsTargetTheEndpointOverASigV4SignedWebSocket() {
        let settings = BrokerConfiguration.settings(infoDictionary: Self.releaseInfo, clientId: "story2client")

        XCTAssertEqual(settings.connectionTarget, "wss://a1b2c3d4e5f6g7-ats.iot.eu-north-1.amazonaws.com:443/mqtt")
        XCTAssertEqual(settings.tlsMode, TlsMode.websocketSigv4)
        XCTAssertEqual(settings.clientId, "story2client")
    }

    // AC36
    // GIVEN the Release Info dictionary without the IoT endpoint
    // WHEN BrokerConfiguration builds the settings
    // THEN the host is empty (as a missing BROKER_HOST is today), so every connect fails and the list shows its error
    func testStory2_releaseSettingsWithoutEndpointHaveAnEmptyHost() {
        var info = Self.releaseInfo
        info.removeValue(forKey: "AmazonIotEndpoint")

        let settings = BrokerConfiguration.settings(infoDictionary: info, clientId: "story2client")

        XCTAssertEqual(settings.host, "")
        XCTAssertEqual(settings.tlsMode, TlsMode.websocketSigv4)
    }

    // AC52
    // GIVEN the Debug Info dictionary with BrokerHost localhost
    // WHEN BrokerConfiguration builds the settings
    // THEN they are host localhost, port 1883 and plaintext MQTT: no WebSocket, no signing
    func testStory2_debugSettingsKeepThePlaintextLanBroker() {
        let settings = BrokerConfiguration.settings(infoDictionary: Self.debugInfo, clientId: "story2client")

        XCTAssertEqual(settings.host, "localhost")
        XCTAssertEqual(settings.port, 1883)
        XCTAssertEqual(settings.tlsMode, TlsMode.none)
        XCTAssertEqual(settings.connectionTarget, "tcp://localhost:1883")
    }
}
