package lt.setkus.pliuskis.data.clientid

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch

/** An MQTT 3.1.1 client ID every broker accepts and that is safe inside a topic (no `/`, `+`, `#`). */
private const val VALID_CLIENT_ID = "^[0-9a-zA-Z]{1,23}$"
private const val GENERATED_SAMPLE_SIZE = 1_000

/**
 * STORY-5 AC20-AC21: each app install gets its own random MQTT client ID, generated once and then read from storage.
 *
 * A "new app process" is a new [ClientIdProvider] over the same [ClientIdStore].
 */
class Story5ClientIdTest : FunSpec({

    // AC20
    // GIVEN a fresh install
    // WHEN a client ID is generated
    // THEN it has only 0-9a-zA-Z and 1 to 23 characters
    test("testStory5_generatedClientIdMatchesMqttSafePattern") {
        generateClientId() shouldMatch VALID_CLIENT_ID
    }

    // AC20
    // GIVEN two installs
    // WHEN each generates a client ID
    // THEN the IDs differ
    test("testStory5_twoGeneratedClientIdsDiffer") {
        generateClientId() shouldNotBe generateClientId()
    }

    // AC20
    // GIVEN many generated IDs
    // THEN every one is valid and no two are the same
    test("testStory5_manyGeneratedClientIdsAreAllValidAndDistinct") {
        val ids = List(GENERATED_SAMPLE_SIZE) { generateClientId() }

        ids.forEach { it shouldMatch VALID_CLIENT_ID }
        ids.toSet() shouldHaveSize GENERATED_SAMPLE_SIZE
    }

    // AC20 + AC21
    // GIVEN a fresh install (nothing stored)
    // WHEN the app asks for its client ID
    // THEN a valid ID is returned and stored
    test("testStory5_freshInstallGeneratesValidIdAndStoresIt") {
        val store = InMemoryClientIdStore()

        val clientId = ClientIdProvider(store).clientId()

        clientId shouldMatch VALID_CLIENT_ID
        store.stored shouldBe clientId
    }

    // AC20
    // GIVEN two fresh installs
    // WHEN each asks for its client ID
    // THEN they get different IDs
    test("testStory5_twoFreshInstallsGetDifferentIds") {
        val first = ClientIdProvider(InMemoryClientIdStore()).clientId()
        val second = ClientIdProvider(InMemoryClientIdStore()).clientId()

        first shouldNotBe second
    }

    // AC21
    // GIVEN a stored client ID
    // WHEN a new app process asks for its client ID
    // THEN it gets the stored ID, and the store is unchanged
    test("testStory5_storedClientIdIsReturnedByNewProcess") {
        val store = InMemoryClientIdStore("abc123")

        val clientId = ClientIdProvider(store).clientId()

        clientId shouldBe "abc123"
        store.stored shouldBe "abc123"
    }

    // AC21
    // GIVEN a stored ID of the maximum valid length (23)
    // WHEN a new app process asks for its client ID
    // THEN the stored ID is kept
    test("testStory5_storedClientIdOfMaximumLengthIsKept") {
        val longest = "A1b2C3d4E5f6G7h8I9j0K1l"
        val store = InMemoryClientIdStore(longest)

        ClientIdProvider(store).clientId() shouldBe longest
        store.stored shouldBe longest
    }

    // AC21
    // GIVEN a fresh install whose first process generated an ID
    // WHEN the app is started again (a new provider over the same store)
    // THEN the second process gets the same ID
    test("testStory5_generatedClientIdSurvivesProcessRestart") {
        val store = InMemoryClientIdStore()

        val firstProcess = ClientIdProvider(store).clientId()
        val secondProcess = ClientIdProvider(store).clientId()

        secondProcess shouldBe firstProcess
    }

    // AC21 + edge case "a missing or corrupt stored client ID"
    // GIVEN an invalid stored value
    // WHEN a new app process asks for its client ID
    // THEN a new valid ID is generated, returned and stored (never the invalid value, never default-client-id),
    //  and the next process gets that new ID
    mapOf(
        "Empty" to "",
        "Slash" to "has/slash",
        "MqttWildcards" to "abc+#",
        "TwentyFourChars" to "a".repeat(24),
        "OldFallback" to "default-client-id",
        "OldDebugId" to "android-debug-client",
    ).forEach { (label, invalid) ->
        test("testStory5_invalidStoredClientId${label}IsReplacedAndStored") {
            val store = InMemoryClientIdStore(invalid)

            val clientId = ClientIdProvider(store).clientId()

            clientId shouldMatch VALID_CLIENT_ID
            clientId shouldNotBe invalid
            clientId shouldNotBe "default-client-id"
            store.stored shouldBe clientId
            ClientIdProvider(store).clientId() shouldBe clientId
        }
    }
})
