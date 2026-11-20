package lt.setkus.pliuskis.data.clientid

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import platform.Foundation.NSUserDefaults

/** 23 characters of 0-9a-zA-Z: the ID `generateClientId` makes. */
private const val GENERATED_CLIENT_ID = "^[0-9a-zA-Z]{23}$"
private const val SUITE_PREFIX = "lt.setkus.pliuskis.story3.clientid"

/** A UserDefaults domain of its own for one test, so tests never see each other's or the app's stored IDs. */
private fun freshDefaults(name: String): NSUserDefaults {
    val suiteName = "$SUITE_PREFIX.$name"
    NSUserDefaults.standardUserDefaults.removePersistentDomainForName(suiteName)
    return NSUserDefaults(suiteName = suiteName)
}

/** The same UserDefaults domain opened again, as a later launch of the app would open it. */
private fun reopenDefaults(name: String): NSUserDefaults = NSUserDefaults(suiteName = "$SUITE_PREFIX.$name")

private fun removeDefaults(name: String) {
    NSUserDefaults.standardUserDefaults.removePersistentDomainForName("$SUITE_PREFIX.$name")
}

/**
 * STORY-3 AC19: each iOS install keeps one MQTT client ID in UserDefaults, as Android keeps it in SharedPreferences.
 * The iOS [ClientIdStore] is [UserDefaultsClientIdStore]; the ID rules come from the common [ClientIdProvider].
 *
 * A "launch" is a new [UserDefaultsClientIdStore] and [ClientIdProvider] over the same UserDefaults domain.
 */
class Story3UserDefaultsClientIdStoreTest : FunSpec({

    // AC19
    // GIVEN an empty UserDefaults
    // WHEN the app asks for its client ID
    // THEN it gets 23 characters from 0-9a-zA-Z, and the next launch reads that same ID from UserDefaults
    test("testStory3_emptyUserDefaultsGeneratesValidIdAndStoresIt") {
        val name = "empty"
        try {
            val clientId = ClientIdProvider(UserDefaultsClientIdStore(freshDefaults(name))).clientId()

            clientId shouldMatch GENERATED_CLIENT_ID
            UserDefaultsClientIdStore(reopenDefaults(name)).read() shouldBe clientId
        } finally {
            removeDefaults(name)
        }
    }

    // AC19
    // GIVEN an empty UserDefaults
    // WHEN nothing was stored yet
    // THEN the store reads null, so the provider knows to generate an ID
    test("testStory3_emptyUserDefaultsReadsNull") {
        val name = "nothingStored"
        try {
            UserDefaultsClientIdStore(freshDefaults(name)).read() shouldBe null
        } finally {
            removeDefaults(name)
        }
    }

    // AC19
    // GIVEN a stored valid ID
    // WHEN the app is launched again
    // THEN the same ID is returned, and UserDefaults still holds it
    test("testStory3_storedValidIdIsReturnedOnNextLaunch") {
        val name = "storedValid"
        try {
            UserDefaultsClientIdStore(freshDefaults(name)).write("abc123XYZ")

            val clientId = ClientIdProvider(UserDefaultsClientIdStore(reopenDefaults(name))).clientId()

            clientId shouldBe "abc123XYZ"
            UserDefaultsClientIdStore(reopenDefaults(name)).read() shouldBe "abc123XYZ"
        } finally {
            removeDefaults(name)
        }
    }

    // AC19
    // GIVEN an empty UserDefaults and a first launch that generated an ID
    // WHEN the app is launched again
    // THEN the second launch gets the first launch's ID
    test("testStory3_generatedIdSurvivesRelaunch") {
        val name = "relaunch"
        try {
            val firstLaunch = ClientIdProvider(UserDefaultsClientIdStore(freshDefaults(name))).clientId()

            val secondLaunch = ClientIdProvider(UserDefaultsClientIdStore(reopenDefaults(name))).clientId()

            secondLaunch shouldBe firstLaunch
        } finally {
            removeDefaults(name)
        }
    }

    // AC19 + edge case "a missing or invalid stored client ID is replaced by a new one"
    // GIVEN a stored invalid value ("" or "a b")
    // WHEN the app asks for its client ID
    // THEN a new valid ID replaces it in UserDefaults, and the next launch gets that new ID
    mapOf(
        "Empty" to "",
        "WithSpace" to "a b",
    ).forEach { (label, invalid) ->
        test("testStory3_storedInvalidValue${label}IsReplacedByNewValidId") {
            val name = "invalid$label"
            try {
                UserDefaultsClientIdStore(freshDefaults(name)).write(invalid)

                val clientId = ClientIdProvider(UserDefaultsClientIdStore(reopenDefaults(name))).clientId()

                clientId shouldMatch GENERATED_CLIENT_ID
                clientId shouldNotBe invalid
                UserDefaultsClientIdStore(reopenDefaults(name)).read() shouldBe clientId
                ClientIdProvider(UserDefaultsClientIdStore(reopenDefaults(name))).clientId() shouldBe clientId
            } finally {
                removeDefaults(name)
            }
        }
    }
})
