package lt.setkus.pliuskis.core.toolchain

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import java.io.File

/**
 * STORY-3 AC20: gson and the AWS IoT SDK are gone. No Kotlin source imports them, and neither the version catalog nor
 * any build script names them.
 *
 * The package names are built from parts, so this file does not match its own checks.
 */
class Story3UnusedDependenciesTest : FunSpec({

    val root = ToolchainBuildFiles.repoRoot
    val gsonPackage = listOf("com", "google", "gson").joinToString(".")
    val awsPackage = listOf("com", "amazonaws").joinToString(".")

    // Lowercase tokens that name the removed dependencies in TOML and Gradle scripts: Maven group/artifact, catalog
    // aliases and version keys (the old key was spelled "amazonwsAndroidIot").
    val forbiddenBuildTokens = listOf("gson", "aws-android-sdk", "amazonaws", "amazonws")

    fun importsOf(packageName: String): Regex =
        Regex("""^\s*import\s+${Regex.escape(packageName)}\b""", RegexOption.MULTILINE)

    fun relative(file: File): String = file.relativeTo(root).path

    test("testStory3_noKotlinSourceImportsGsonOrAwsSdk") {
        val sources = ToolchainBuildFiles.moduleKotlinSources
        withClue("Expected to scan the Kotlin sources of the modules") { sources.shouldNotBeEmpty() }
        val offenders = sources.filter { file ->
            val text = file.readText()
            importsOf(gsonPackage).containsMatchIn(text) || importsOf(awsPackage).containsMatchIn(text)
        }.map(::relative)
        withClue("Kotlin sources importing $gsonPackage or $awsPackage") { offenders.shouldBeEmpty() }
    }

    test("testStory3_versionCatalogHasNoGsonOrAwsSdk") {
        val text = File(root, "gradle/libs.versions.toml").readText().lowercase()
        val found = forbiddenBuildTokens.filter { text.contains(it) }
        withClue("gradle/libs.versions.toml names removed dependencies") { found.shouldBeEmpty() }
    }

    test("testStory3_noBuildScriptReferencesGsonOrAwsSdk") {
        val scripts = (ToolchainBuildFiles.gradleScripts + ToolchainBuildFiles.buildLogicSources).distinct()
        withClue("Expected to scan the Gradle scripts") { scripts.shouldNotBeEmpty() }
        val offenders = scripts.associate { file ->
            val text = file.readText().lowercase()
            relative(file) to forbiddenBuildTokens.filter { text.contains(it) }
        }.filterValues { it.isNotEmpty() }
        withClue("Build scripts naming removed dependencies: $offenders") { offenders.keys.shouldBeEmpty() }
    }
})
