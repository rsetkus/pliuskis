package lt.setkus.pliuskis.core.toolchain

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * STORY-2 (kmp_extract_shared_library, PLIUS-13): tests of shared code use hand-written fakes instead of MockK.
 *
 * - AC7: the six converted test classes have no `io.mockk` import.
 * - AC8: in the source sets of core/domain, core/data and feature/devices, only
 *   HiveMqttClientManagerPendingConnectionTest imports `io.mockk`.
 * - AC9 (structural part): each converted class still declares every test name it had on master at 78f5079. That no
 *   assertion is removed or weakened is a review check (the implementation map lists before/after counts).
 *
 * Only import lines count: a file may mention MockK in a string or a comment.
 */
class Story2MockkFreeSharedTestsTest : FunSpec({

    val modules = listOf("core/domain", "core/data", "feature/devices")
    val mockkImport = Regex("""^\s*import\s+io\.mockk\b""", RegexOption.MULTILINE)
    val allowedMockkUser = "HiveMqttClientManagerPendingConnectionTest.kt"

    /** Every Kotlin file under `src/` of the given module, outside build output. */
    fun moduleSources(module: String): List<File> =
        File(ToolchainBuildFiles.repoRoot, "$module/src").walkTopDown()
            .onEnter { it.name != "build" }
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    /** The single file named `<className>.kt` in the module's sources. */
    fun testFile(module: String, className: String): File {
        val matches = moduleSources(module).filter { it.name == "$className.kt" }
        withClue("Expected exactly one $className.kt under $module/src, found $matches") {
            matches shouldHaveSize 1
        }
        return matches.first()
    }

    fun importsMockk(file: File): Boolean = mockkImport.containsMatchIn(file.readText())

    /** Names of `test("...")` / `test("""...""")` calls in a Kotest spec, whitespace collapsed. */
    fun testNames(file: File): List<String> {
        val call = Regex("\\btest\\(\\s*(?:\"\"\"(.*?)\"\"\"|\"((?:[^\"\\\\]|\\\\.)*)\")", RegexOption.DOT_MATCHES_ALL)
        return call.findAll(file.readText())
            .map { match -> match.groupValues[1].ifEmpty { match.groupValues[2] } }
            .map { it.replace(Regex("""\s+"""), " ").trim() }
            .toList()
    }

    test("testStory2_convertedTestClassesHaveNoMockkImport") {
        val withMockk = CONVERTED_TESTS
            .map { testFile(it.module, it.className) }
            .filter { importsMockk(it) }
            .map { it.name }
        withClue("These converted test classes still import io.mockk") {
            withMockk.shouldBeEmpty()
        }
    }

    test("testStory2_onlyHiveMqttPendingConnectionTestImportsMockk") {
        val withMockk = modules
            .flatMap { moduleSources(it) }
            .filter { importsMockk(it) }
            .map { it.relativeTo(ToolchainBuildFiles.repoRoot).invariantSeparatorsPath }
        withClue("Only $allowedMockkUser may import io.mockk in ${modules.joinToString()}") {
            withMockk.map { it.substringAfterLast('/') } shouldContainExactly listOf(allowedMockkUser)
        }
    }

    test("testStory2_hiveMqttPendingConnectionTestStaysInCoreData") {
        withClue("$allowedMockkUser stays an Android test of core/data") {
            moduleSources("core/data").any { it.name == allowedMockkUser } shouldBe true
        }
    }

    test("testStory2_convertedTestClassesKeepEveryTestNameFromMaster") {
        val missing = CONVERTED_TESTS.flatMap { converted ->
            val names = testNames(testFile(converted.module, converted.className)).toSet()
            converted.namesOnMaster.filterNot { it in names }.map { "${converted.className}: $it" }
        }
        withClue("Test cases removed or renamed since master 78f5079") {
            missing.shouldBeEmpty()
        }
    }

    test("testStory2_convertedTestClassesHaveAtLeastTheirTestCountFromMaster") {
        val shrunk = CONVERTED_TESTS
            .map { it to testNames(testFile(it.module, it.className)).size }
            .filter { (converted, count) -> count < converted.namesOnMaster.size }
            .map { (converted, count) -> "${converted.className}: $count < ${converted.namesOnMaster.size}" }
        withClue("Converted test classes with fewer test cases than on master 78f5079") {
            shrunk.shouldBeEmpty()
        }
    }
}) {
    private class ConvertedTest(val module: String, val className: String, val namesOnMaster: List<String>)

    private companion object {
        /**
         * Test names on master at 78f5079 (whitespace collapsed); 19 test cases in 6 classes. STORY-4 removed
         * `testStory2_connectRunsOnInjectedBackgroundDispatcher` along with the I/O dispatcher it checked.
         */
        val CONVERTED_TESTS = listOf(
            ConvertedTest(
                "core/domain",
                "ObserveDevicesUseCaseTest",
                listOf(
                    "GIVEN a device's retained info and online availability WHEN devices are observed " +
                        "THEN should emit the device as online",
                    "GIVEN an online device WHEN the broker publishes its Last Will " +
                        "THEN should emit the device as offline",
                    "GIVEN a malformed info payload followed by a valid one WHEN devices are observed " +
                        "THEN should skip the malformed device and keep emitting",
                    "GIVEN a known device WHEN an empty retained info payload arrives THEN should remove the device",
                    "GIVEN availability that arrives before the device's info WHEN the info arrives " +
                        "THEN should apply the earlier availability",
                    "GIVEN several devices WHEN devices are observed THEN should emit them sorted by name",
                    "GIVEN an info payload whose deviceId differs from its topic WHEN devices are observed " +
                        "THEN should use the deviceId from the topic",
                    "GIVEN the subscription itself fails WHEN devices are observed THEN should emit the error",
                ),
            ),
            ConvertedTest(
                "core/domain",
                "CommandGetSystemStateLevelUseCaseTest",
                listOf(
                    "GIVEN water level repository, WHEN use case to get water level is executed, " +
                        "THEN should initiate repository to send a signal",
                ),
            ),
            ConvertedTest(
                "core/data",
                "Story2ConnectTest",
                listOf("testStory2_rejectedConnAckEmitsLeft"),
            ),
            ConvertedTest(
                "core/data",
                "SubscribeDeviceTopicsTest",
                listOf(
                    "GIVEN retained messages on the device discovery topics WHEN devices are subscribed " +
                        "THEN should map them to events with the deviceId taken from the topic",
                    "GIVEN messages on malformed topics WHEN devices are subscribed THEN should drop them",
                ),
            ),
            ConvertedTest(
                "core/data",
                "AwsSystemStateRequestTest",
                listOf(
                    "GIVEN aws manager WHEN published message to aws manager for system update " +
                        "THEN should complete without error",
                ),
            ),
            ConvertedTest(
                "feature/devices",
                "Story2DevicesViewModelTest",
                listOf(
                    "testStory2_connectSucceedsThenDevicesShowAsSuccess",
                    "testStory2_pendingConnectKeepsLoadingWithoutSubscribing",
                    "testStory2_connectFailureShowsErrorWithoutSubscribing",
                    "testStory2_alreadyConnectedClientStillShowsDevices",
                    "testStory2_subscriptionFailureShowsError",
                    "testStory2_noDevicesWithinTimeoutShowsEmptyList",
                ),
            ),
        )
    }
}
