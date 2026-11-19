package lt.setkus.pliuskis.core.toolchain

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.util.concurrent.TimeUnit

private const val SECRETS_PLUGIN_ALIAS = "secrets"
private const val KEYSTORE_NAME = "legacy-keystore.p12"
private const val OLD_KEYSTORE_PATH = "core/data/src/main/assets/$KEYSTORE_NAME"
private const val APP_KEYSTORE_PATH = "app/src/main/assets/$KEYSTORE_NAME"
private const val SETTINGS_CLASS = "MqttSettings"
private const val GIT_TIMEOUT_SECONDS = 30L

/** Kotlin files of the production source sets of `core/data` (every source set whose name doesn't contain "test"). */
private val coreDataMainSources: List<File> by lazy {
    ToolchainBuildFiles.moduleKotlinSources.filter { file ->
        val path = file.relativeTo(ToolchainBuildFiles.repoRoot).invariantSeparatorsPath
        val sourceSet = path.substringAfter("/src/").substringBefore('/')
        path.startsWith("core/data/src/") && !sourceSet.contains("test", ignoreCase = true)
    }
}

private fun relative(file: File) = file.relativeTo(ToolchainBuildFiles.repoRoot).invariantSeparatorsPath

private fun secretsPluginId(): String =
    checkNotNull(ToolchainBuildFiles.catalog.plugins[SECRETS_PLUGIN_ALIAS]) { "No secrets plugin in the catalog" }
        .coordinate

/** Runs git in the repository root and returns its exit code and standard output. */
private fun git(vararg args: String): Pair<Int, String> {
    val process = ProcessBuilder(listOf("git", *args))
        .directory(ToolchainBuildFiles.repoRoot)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    check(process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "git ${args.joinToString(" ")} timed out" }
    return process.exitValue() to output
}

/**
 * STORY-6 AC26, AC27 and AC29: `core/data` reads no BuildConfig and no secrets; the app module owns the secrets
 * plugin and the keystore, and no secret file is tracked by git.
 */
class Story6BrokerSettingsSourcesTest : FunSpec({

    // AC26
    // GIVEN the main sources of core/data
    // THEN none imports or references BuildConfig
    test("testStory6_coreDataSourcesHaveNoBuildConfig") {
        coreDataMainSources.shouldNotBeEmpty()
        val buildConfigUse = Regex("""import\s+[\w.]*\bBuildConfig\b|\bBuildConfig\.\w""")

        val offenders = coreDataMainSources.filter { buildConfigUse.containsMatchIn(it.readText()) }.map(::relative)

        offenders.shouldBeEmpty()
    }

    // AC26
    // GIVEN core/data/build.gradle.kts and app/build.gradle.kts
    // THEN core/data does not apply the secrets plugin (nor configure it) and app does
    test("testStory6_secretsPluginMovedFromCoreDataToApp") {
        val pluginId = secretsPluginId()

        ToolchainBuildFiles.appliedPluginIds(":core:data") shouldNotContain pluginId
        ToolchainBuildFiles.moduleScript(":core:data").readText() shouldNotContain Regex("""(?m)^\s*secrets\s*\{""")
        ToolchainBuildFiles.appliedPluginIds(":app") shouldContain pluginId
    }

    // AC26
    // GIVEN the main sources of core/data
    // THEN the old MqttConfiguration (which held a javax.net.ssl.KeyManagerFactory) is gone
    test("testStory6_mqttConfigurationWithKeyManagerFactoryIsRemoved") {
        val offenders = coreDataMainSources
            .filter { Regex("""\bMqttConfiguration\b""").containsMatchIn(it.readText()) }
            .map(::relative)

        offenders.shouldBeEmpty()
    }

    // AC26, AC27
    // GIVEN the file of core/data that declares the settings type MqttSettings
    // THEN it holds clientId, host, port and tlsMode and uses no android, javax or java type
    test("testStory6_settingsTypeIsPlatformNeutral") {
        val declaration = Regex("""\bclass\s+$SETTINGS_CLASS\b""")
        val files = coreDataMainSources.filter { declaration.containsMatchIn(it.readText()) }
        files shouldHaveSize 1
        val text = files.single().readText()

        val constructor = text.substringAfter("class $SETTINGS_CLASS").substringBefore(")")
        val properties = Regex("""\bval\s+(\w+)\s*:""").findAll(constructor).map { it.groupValues[1] }.toList()
        properties shouldContainAll listOf("clientId", "host", "port", "tlsMode")
        Regex("""\b(android|androidx|javax|java)\.\w""").findAll(text).map { it.value }.toList().shouldBeEmpty()
        text shouldNotContain "KeyManagerFactory"
    }

    // AC29
    // GIVEN the repository
    // THEN legacy-keystore.p12 is not under core/data/src/main/assets
    test("testStory6_keystoreNotInCoreDataAssets") {
        File(ToolchainBuildFiles.repoRoot, OLD_KEYSTORE_PATH).exists() shouldBe false
    }

    // AC29
    // GIVEN the app module's keystore location app/src/main/assets/legacy-keystore.p12
    // THEN git ignores it
    test("testStory6_appKeystoreLocationIsGitignored") {
        val (exitCode, output) = git("check-ignore", "--no-index", "--quiet", APP_KEYSTORE_PATH)

        withClue("git check-ignore output: $output") { exitCode shouldBe 0 }
    }

    // AC29
    // GIVEN the files tracked by git
    // THEN none is a secret file (keystore, certificate, key or secrets.properties)
    test("testStory6_noSecretFileIsTracked") {
        val (exitCode, output) = git("ls-files")
        exitCode shouldBe 0
        val secretFile = Regex("""(\.p12|\.pem|\.pem\.crt|\.pem\.key|(^|/)secrets\.properties)$""")

        val tracked = output.lineSequence().filter { secretFile.containsMatchIn(it.trim()) }.toList()

        tracked.shouldBeEmpty()
    }
})
