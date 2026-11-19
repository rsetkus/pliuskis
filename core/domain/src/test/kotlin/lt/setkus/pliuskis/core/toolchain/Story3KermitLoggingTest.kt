package lt.setkus.pliuskis.core.toolchain

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import java.io.File

/**
 * STORY-3 (kmp_extract_shared_library, PLIUS-14): core/domain and core/data log through Kermit instead of Timber.
 *
 * - AC10: no main source of core/domain or core/data imports Timber; the converted files import Kermit and the
 *   module that compiles them gets Kermit from its build script.
 * - AC11: core/domain/build.gradle.kts has no `api(libs.timber)`; the catalog has a Kermit library whose version
 *   comes from the `[versions]` table.
 * - AC12 (structural part): each converted call keeps the level and message text it had on master at 78f5079, and
 *   each converted file has no more log calls than before. The before/after list is a manual check in the
 *   implementation map.
 * - AC13 (structural part): calls that passed a throwable to Timber still pass it, so Logcat keeps the stack trace.
 *   The Logcat comparison on the emulator is a manual check.
 *
 * Kermit's level functions are `v`, `d`, `i`, `w`, `e` and `a`; a call may take the message as a string argument
 * (`log.w("text", error)`) or as a trailing lambda (`log.w(error) { "text" }`). Both forms count.
 */
class Story3KermitLoggingTest : FunSpec({

    val sharedModules = listOf("core/domain", "core/data")
    val timberImport = Regex("""^\s*import\s+timber\.log\b""", RegexOption.MULTILINE)
    val kermitImport = Regex("""^\s*import\s+co\.touchlab\.kermit\b""", RegexOption.MULTILINE)
    val kermitCoordinate = "co.touchlab:kermit"

    /** Every Kotlin file under `src/main` of the given module, outside build output. */
    fun mainSources(module: String): List<File> =
        File(ToolchainBuildFiles.repoRoot, "$module/src/main").walkTopDown()
            .onEnter { it.name != "build" }
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    fun sourceFile(snapshot: LogSnapshot): File {
        val file = File(ToolchainBuildFiles.repoRoot, snapshot.path)
        withClue("${snapshot.path} must still exist (STORY-3 converts its log calls, it does not move the file)") {
            file.isFile shouldBe true
        }
        return file
    }

    /** The catalog alias of the Kermit library (`co.touchlab:kermit`), or null when the catalog has none. */
    fun kermitAlias(): String? =
        ToolchainBuildFiles.catalog.libraries.entries.firstOrNull { it.value.coordinate == kermitCoordinate }?.key

    /** `libs.<alias>` accessor of a catalog alias, as a Gradle script writes it. */
    fun accessorRegex(alias: String, configuration: String): Regex {
        val accessor = "libs." + alias.replace('-', '.').replace('_', '.')
        return Regex("""\b$configuration\(\s*${Regex.escape(accessor)}\s*\)""")
    }

    test("testStory3_noMainSourceOfCoreDomainOrCoreDataImportsTimber") {
        val withTimber = sharedModules
            .flatMap { mainSources(it) }
            .filter { timberImport.containsMatchIn(it.readText()) }
            .map { it.relativeTo(ToolchainBuildFiles.repoRoot).invariantSeparatorsPath }
        withClue("These main sources still import timber.log") {
            withTimber.shouldBeEmpty()
        }
    }

    test("testStory3_convertedFilesLogThroughKermit") {
        val notKermit = CONVERTED_FILES
            .map { it to sourceFile(it).readText() }
            .filter { (_, text) -> !kermitImport.containsMatchIn(text) || text.contains("Timber.") }
            .map { (snapshot, _) -> snapshot.path }
        withClue("These files must import co.touchlab.kermit and have no Timber call left") {
            notKermit.shouldBeEmpty()
        }
    }

    test("testStory3_modulesThatLogGetKermitFromTheirBuildScript") {
        val alias = kermitAlias()
        withClue("gradle/libs.versions.toml needs a library entry for $kermitCoordinate") {
            alias.shouldNotBeNull()
        }
        val domainScript = ToolchainBuildFiles.moduleScript(":core:domain").readText()
        val domainExportsKermit = accessorRegex(checkNotNull(alias), "api").containsMatchIn(domainScript)
        val missing = sharedModules.filter { module ->
            val script = ToolchainBuildFiles.moduleScript(":" + module.replace('/', ':')).readText()
            val declares = accessorRegex(alias, "(?:api|implementation)").containsMatchIn(script)
            val inherits = module == "core/data" && domainExportsKermit
            !(declares || inherits)
        }
        withClue("These modules call Kermit but get no Kermit dependency (own script, or api from core:domain)") {
            missing.shouldBeEmpty()
        }
    }

    test("testStory3_coreDomainNoLongerExposesTimber") {
        val script = ToolchainBuildFiles.moduleScript(":core:domain").readText()
        withClue("core/domain/build.gradle.kts must not export Timber") {
            script shouldNotContain "api(libs.timber)"
        }
    }

    test("testStory3_kermitVersionComesFromTheVersionsTable") {
        val catalog = ToolchainBuildFiles.catalog
        val entry = catalog.libraries.values.firstOrNull { it.coordinate == kermitCoordinate }
        withClue("gradle/libs.versions.toml needs a library entry for $kermitCoordinate") {
            entry.shouldNotBeNull()
        }
        val kermit = checkNotNull(entry)
        withClue("The Kermit entry must use version.ref, not an inline version") {
            kermit.inlineVersion shouldBe null
            kermit.versionRef.shouldNotBeNull()
        }
        withClue("version.ref = \"${kermit.versionRef}\" must name an entry of [versions]") {
            catalog.resolveVersion(kermit).shouldNotBeNull()
        }
    }

    test("testStory3_convertedCallsKeepTheirLevelAndMessageText") {
        val lost = CONVERTED_FILES.flatMap { snapshot ->
            val calls = KermitCalls.find(sourceFile(snapshot).readText())
            snapshot.calls
                .filterNot { expected -> calls.any { it.level == expected.level && expected.message in it.messages } }
                .map { "${snapshot.path}: ${describe(it)}" }
        }
        withClue("No Kermit call with the same level and message text as on master 78f5079") {
            lost.shouldBeEmpty()
        }
    }

    test("testStory3_convertedFilesAddNoLogCalls") {
        val grown = CONVERTED_FILES
            .map { it to KermitCalls.find(sourceFile(it).readText()).size }
            .filter { (snapshot, count) -> count > snapshot.calls.size }
            .map { (snapshot, count) -> "${snapshot.path}: $count log calls, ${snapshot.calls.size} on master" }
        withClue("A converted file logs more than before; a new call may log a payload or a secret") {
            grown.shouldBeEmpty()
        }
    }

    test("testStory3_convertedCallsKeepTheirThrowable") {
        val lost = CONVERTED_FILES.flatMap { snapshot ->
            val calls = KermitCalls.find(sourceFile(snapshot).readText())
            snapshot.calls
                .filter { it.throwable != null }
                .filterNot { expected ->
                    calls.any { call ->
                        call.level == expected.level &&
                            expected.message in call.messages &&
                            Regex("""\b${expected.throwable}\b""").containsMatchIn(call.text)
                    }
                }
                .map { "${snapshot.path}: ${describe(it)} must still pass ${it.throwable}" }
        }
        withClue("These calls dropped the throwable they passed to Timber (Logcat would lose the stack trace)") {
            lost.shouldBeEmpty()
        }
    }
}) {
    /** A Timber call on master at 78f5079: level letter, message template as written in source, throwable name. */
    private class LoggedCall(val level: Char, val message: String, val throwable: String? = null)

    private class LogSnapshot(val path: String, val calls: List<LoggedCall>)

    /** A Kermit level call found in source: level letter, full call text, string literals inside it. */
    private class KermitCall(val level: Char, val text: String, val messages: List<String>)

    private object KermitCalls {
        private val levelCall = Regex("""\.\s*([vdiwea])\s*\(""")
        private val stringLiteral = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")

        fun find(source: String): List<KermitCall> =
            levelCall.findAll(source).map { match ->
                val text = callText(source, match.range.last)
                val messages = stringLiteral.findAll(text).map { it.groupValues[1] }.toList()
                KermitCall(match.groupValues[1].single(), text, messages)
            }.toList()

        /** The argument list starting at [openParen], plus a trailing lambda when one follows. */
        private fun callText(source: String, openParen: Int): String {
            val argsEnd = closing(source, openParen, '(', ')')
            val next = (argsEnd + 1 until source.length).firstOrNull { !source[it].isWhitespace() }
            val end = if (next != null && source[next] == '{') closing(source, next, '{', '}') else argsEnd
            return source.substring(openParen, end + 1)
        }

        private fun closing(source: String, start: Int, open: Char, close: Char): Int {
            var depth = 0
            var index = start
            var found = source.length - 1
            while (index < source.length) {
                when (source[index]) {
                    open -> depth++
                    close -> depth--
                }
                if (depth == 0) {
                    found = index
                    index = source.length
                }
                index++
            }
            return found
        }
    }

    private companion object {
        fun describe(call: LoggedCall): String = "${call.level}(\"${call.message}\")"

        /** Timber calls of core/domain and core/data main sources on master at 78f5079. */
        val CONVERTED_FILES = listOf(
            LogSnapshot(
                "core/domain/src/main/kotlin/lt/setkus/pliuskis/core/devicelist/ObserveDevicesUseCase.kt",
                listOf(LoggedCall('w', "Skipping malformed info from device \${event.deviceId}", "error")),
            ),
            LogSnapshot(
                "core/data/src/main/kotlin/lt/setkus/pliuskis/data/mqttclient/HiveMqttClientManager.kt",
                listOf(LoggedCall('d', "MQTT client is already connecting", "alreadyConnecting")),
            ),
            LogSnapshot(
                "core/data/src/main/kotlin/lt/setkus/pliuskis/data/devices/SubscribeDeviceTopics.kt",
                listOf(LoggedCall('w', "Ignoring message on malformed device topic: \$topic")),
            ),
        )
    }
}
