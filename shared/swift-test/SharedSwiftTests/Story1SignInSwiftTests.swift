import Shared
import XCTest

/// STORY-1 (kmp_cognito_login, PLIUS-34), AC27 (Swift side): Swift creates the sign-in and session ViewModels through
/// the `Shared` factories, without touching Koin, and reads their states as Kotlin state classes. The Kotlin test
/// `Story1SignInSharedApiTest` drives them through SigningIn, SignedIn and Error with fake ports.
///
/// Koin can be started only once per process; `initShared` does nothing when the graph is already running, so this
/// test works whether it runs before or after `Story10SharedEntryPointSwiftTests`.
final class Story1SignInSwiftTests: XCTestCase {

    @MainActor
    func testStory1_signInAndSessionViewModelsFromFactoriesShowKotlinStates() throws {
        let settings = MqttSettings(clientId: "story1swift", host: "localhost", port: 1883, tlsMode: TlsMode.none)
        try SharedEntryPointKt.doInitShared(settings: settings, transport: NoBrokerTransport())

        let signIn = SharedEntryPointKt.createSignInViewModel()
        XCTAssertTrue(signIn.state.value is SignInStateSignedOut, "A new sign-in ViewModel starts SignedOut")

        // The stored session is checked at launch; on a test process with no stored session it is Checking or,
        // once checked, SignedOut.
        let session = SharedEntryPointKt.createSessionViewModel()
        let sessionState = session.state.value
        XCTAssertTrue(
            sessionState is SessionStateChecking || sessionState is SessionStateSignedOut,
            "A new session ViewModel without a stored session is \(String(describing: sessionState))"
        )
    }
}

/// A broker that refuses every connection; sign-in needs no MQTT.
private final class NoBrokerTransport: NSObject, MqttTransport {
    var listener: MqttTransportListener?

    func connect(host: String, port: Int32, clientId: String) {
        listener?.onDisconnected()
    }

    func disconnect() {}
    func subscribe(topicFilter: String) {}

    func unsubscribe(topicFilter: String) {}

    func publish(topic: String, payload: Data) -> Int32 { -1 }
}
