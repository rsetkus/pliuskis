package lt.setkus.pliuskis.core.toolchain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

private const val IOT_MANAGER_FILE = "core/data/src/main/kotlin/lt/setkus/pliuskis/data/IotManager.kt"
private const val CORE_DATA_MAIN = "core/data/src/main"
private const val DEVICES_VIEW_MODEL_FILE =
    "feature/devices/src/main/kotlin/lt/setkus/pliuskis/feature/devices/DevicesViewModel.kt"
private const val DEVICE_DISCOVERY_DOC = "docs/features/device-discovery.md"
private const val PREVIOUS_LAST_VERIFIED = "2026-10-15"

/**
 * Connect behaviour tests that existed before STORY-4, by file. They may change only for the new suspend
 * signatures, so every one of these test names must still be there.
 * `testStory2_connectRunsOnInjectedBackgroundDispatcher` is not listed: it checks the I/O dispatcher that AC17 removes.
 */
private val EXISTING_CONNECT_TESTS = mapOf(
    "core/data/src/test/kotlin/lt/setkus/pliuskis/data/mqttclient/HiveMqttClientManagerPendingConnectionTest.kt" to
        listOf(
            "GIVEN a reconnect in progress that completes WHEN connecting THEN reports connected, no second connect",
            "GIVEN a reconnect that never completes WHEN connecting THEN the connect use case emits a Left",
            "GIVEN another caller starts connecting first WHEN connecting THEN waits for that attempt",
            "GIVEN that concurrent attempt fails WHEN connecting THEN reports failed",
        ),
    "core/data/src/test/kotlin/lt/setkus/pliuskis/data/Story2ConnectTest.kt" to
        listOf("testStory2_rejectedConnAckEmitsLeft"),
    "feature/devices/src/test/kotlin/lt/setkus/pliuskis/feature/devices/Story4DevicesViewModelRetryTest.kt" to
        listOf(
            "testStory4_retryAfterFailedConnectShowsLoadingThenDevices",
            "testStory4_repeatedRetriesWhileConnectingConnectOnce",
        ),
    "feature/devices/src/test/kotlin/lt/setkus/pliuskis/feature/devices/Story2DevicesViewModelTest.kt" to
        listOf(
            "testStory2_connectSucceedsThenDevicesShowAsSuccess",
            "testStory2_pendingConnectKeepsLoadingWithoutSubscribing",
            "testStory2_connectFailureShowsErrorWithoutSubscribing",
            "testStory2_alreadyConnectedClientStillShowsDevices",
            "testStory2_subscriptionFailureShowsError",
            "testStory2_noDevicesWithinTimeoutShowsEmptyList",
        ),
    "feature/control/src/test/kotlin/lt/setkus/feature/control/ControlViewModelConnectionTest.kt" to
        listOf(
            "GIVEN a restored control screen and a disconnected client " +
                "WHEN it starts THEN connects before status calls",
            "GIVEN the connection fails WHEN the control screen starts THEN shows an error without status calls",
        ),
)

private fun repoFile(path: String): File = File(ToolchainBuildFiles.repoRoot, path)

/** The KDoc block (`/** ... */`) right before the `interface IotManager` declaration. */
private fun iotManagerKDoc(source: String): String {
    val declaration = source.indexOf("interface IotManager")
    val kdocStart = source.lastIndexOf("/**", declaration)
    val kdoc = if (declaration >= 0 && kdocStart >= 0) source.substring(kdocStart, declaration) else ""
    return kdoc
}

/** Kotlin code with `//` line comments and `/* */` block comments removed, so a mention in a comment doesn't count. */
private fun withoutComments(source: String): String =
    source.replace(Regex("""/\*[\s\S]*?\*/"""), "").replace(Regex("""//[^\n]*"""), "")

/**
 * STORY-4: Make IotManager non-blocking.
 * Structural checks for AC14, AC15, AC17 and AC19 (docs part), and the AC18 guard that the existing connect tests are
 * kept. The behaviour parts live in core:data `Story4NonBlockingConnectTest` and feature:devices
 * `Story4NonBlockingRetryTest`.
 */
class Story4NonBlockingIotManagerSourcesTest : FunSpec({

    // AC14
    // GIVEN the IotManager interface
    // WHEN its declarations are read
    // THEN connect() and publishString(...) should be suspend functions
    test("testStory4_iotManagerConnectAndPublishStringAreSuspend") {
        val source = withoutComments(repoFile(IOT_MANAGER_FILE).readText())

        source shouldContain Regex("""suspend\s+fun\s+connect\s*\(""")
        source shouldContain Regex("""suspend\s+fun\s+publishString\s*\(""")
    }

    // AC14
    // GIVEN the IotManager interface
    // WHEN its KDoc is read
    // THEN it should state the contract: connect is safe when already connected or connecting, and subscriptions
    // survive a reconnect
    test("testStory4_iotManagerKDocStatesTheConnectContract") {
        val kdoc = iotManagerKDoc(repoFile(IOT_MANAGER_FILE).readText()).lowercase()

        kdoc shouldContain "already connected"
        kdoc shouldContain "connecting"
        kdoc shouldContain "reconnect"
        kdoc shouldContain "subscription"
    }

    // AC15
    // GIVEN the core/data main sources
    // WHEN their code is read
    // THEN none should call Thread.sleep or System.currentTimeMillis
    test("testStory4_coreDataMainSourcesDoNotSleepOrReadTheWallClock") {
        val sources = repoFile(CORE_DATA_MAIN).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val offenders = sources.filter { file ->
            val code = withoutComments(file.readText())
            code.contains("Thread.sleep") || code.contains("System.currentTimeMillis")
        }

        sources.size shouldBeGreaterThan 0
        offenders.map { it.relativeTo(ToolchainBuildFiles.repoRoot).path }.shouldBeEmpty()
    }

    // AC17
    // GIVEN DevicesViewModel
    // WHEN its code is read
    // THEN it should use no AtomicBoolean and no ioDispatcher for connect
    test("testStory4_devicesViewModelHasNoAtomicBooleanAndNoIoDispatcher") {
        val code = withoutComments(repoFile(DEVICES_VIEW_MODEL_FILE).readText())

        code shouldNotContain "AtomicBoolean"
        code shouldNotContain "ioDispatcher"
    }

    // AC18
    // GIVEN the connect behaviour tests that existed before this story
    // WHEN their files are read
    // THEN every one of those tests should still exist (they change only for the suspend signatures)
    test("testStory4_existingConnectTestsAreKept") {
        val missing = EXISTING_CONNECT_TESTS.flatMap { (path, names) ->
            val file = repoFile(path)
            val text = if (file.isFile) file.readText() else ""
            names.filterNot { text.contains("test(\"$it\")") }.map { "$path: $it" }
        }

        EXISTING_CONNECT_TESTS.values.flatten() shouldHaveSize 15
        missing.shouldBeEmpty()
    }

    // AC19
    // GIVEN docs/features/device-discovery.md
    // WHEN it is read
    // THEN it should no longer say that the connect call blocks or runs on an I/O dispatcher, and it should describe
    // the suspending (non-blocking) connect
    test("testStory4_deviceDiscoveryDocDescribesNonBlockingConnect") {
        val doc = repoFile(DEVICE_DISCOVERY_DOC).readText()
        val lower = doc.lowercase()

        lower shouldNotContain "connect call blocks"
        lower shouldNotContain "i/o dispatcher"
        lower shouldNotContain "blocking connects"
        lower shouldContain Regex("""\bsuspend|non-blocking""")
    }

    // AC19
    // GIVEN docs/features/device-discovery.md
    // WHEN its Last verified line is read
    // THEN the date should be newer than the one before this story
    test("testStory4_deviceDiscoveryDocHasNewLastVerifiedDate") {
        val doc = repoFile(DEVICE_DISCOVERY_DOC).readText()
        val date = Regex("""\*\*Last verified\*\*:\s*(\d{4}-\d{2}-\d{2})""").find(doc)?.groupValues?.get(1).orEmpty()

        (date > PREVIOUS_LAST_VERIFIED) shouldBe true
    }
})
