package lt.setkus.pliuskis.shared

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import lt.setkus.pliuskis.data.clientid.UserDefaultsClientIdStore
import platform.Foundation.NSUserDefaults

/** 1 to 23 characters of 0-9a-zA-Z: a client ID every MQTT 3.1.1 broker accepts and that is safe inside a topic. */
private const val VALID_CLIENT_ID = "^[0-9a-zA-Z]{1,23}$"

/**
 * STORY-3 AC19, the Swift-facing side: the app gets its per-install MQTT client ID from `installClientId()` (Swift:
 * `InstallClientIdKt.installClientId()`) and puts it in the `MqttSettings` it passes to `initShared`. The ID lives in
 * the app's standard UserDefaults, through [UserDefaultsClientIdStore], so every launch gets the same one.
 */
class Story3InstallClientIdTest : FunSpec({

    // AC19
    // GIVEN the app process
    // WHEN Swift asks for the install client ID
    // THEN it is a valid client ID, and it is the one stored in the standard UserDefaults
    test("testStory3_installClientIdIsValidAndStoredInStandardUserDefaults") {
        val clientId = installClientId()

        clientId shouldMatch VALID_CLIENT_ID
        UserDefaultsClientIdStore(NSUserDefaults.standardUserDefaults).read() shouldBe clientId
    }

    // AC19
    // GIVEN the app asked for its install client ID once
    // WHEN it asks again (a later launch reads the same UserDefaults)
    // THEN it gets the same ID
    test("testStory3_installClientIdIsTheSameOnEveryCall") {
        val first = installClientId()

        installClientId() shouldBe first
    }
})
