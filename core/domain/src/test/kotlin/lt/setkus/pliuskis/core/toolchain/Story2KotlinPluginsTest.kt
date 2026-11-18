package lt.setkus.pliuskis.core.toolchain

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.string.shouldNotContain

/**
 * STORY-2 AC12: AGP built-in Kotlin replaces `org.jetbrains.kotlin.android`, and every Compose module applies the
 * Kotlin Compose compiler Gradle plugin (directly or through a convention plugin).
 */
class Story2KotlinPluginsTest : FunSpec({

    val composeModules = listOf(":app", ":core:designsystem", ":feature:devices", ":feature:control")

    test("testStory2_catalogDeclaresNoKotlinAndroidPlugin") {
        val kotlinAndroidAliases = ToolchainBuildFiles.catalog.plugins
            .filterValues { it.coordinate == ToolchainBuildFiles.KOTLIN_ANDROID_PLUGIN_ID }
            .keys
        kotlinAndroidAliases.shouldBeEmpty()
    }

    test("testStory2_noBuildScriptOrBuildLogicSourceAppliesKotlinAndroid") {
        val root = ToolchainBuildFiles.repoRoot
        val scripts = listOf(java.io.File(root, "build.gradle.kts")) +
            ToolchainBuildFiles.modulePaths.map { ToolchainBuildFiles.moduleScript(it) } +
            ToolchainBuildFiles.buildLogicSources
        scripts.forEach { file ->
            withClue(file.relativeTo(root).path) {
                val text = file.readText()
                text shouldNotContain ToolchainBuildFiles.KOTLIN_ANDROID_PLUGIN_ID
                text shouldNotContain """kotlin("android")"""
                text shouldNotContain "kotlin-android"
            }
        }
    }

    test("testStory2_noModuleAppliesKotlinAndroidThroughConventionPlugins") {
        ToolchainBuildFiles.modulePaths.forEach { module ->
            withClue(module) {
                val applied = ToolchainBuildFiles.appliedPluginIds(module)
                applied shouldNotContain ToolchainBuildFiles.KOTLIN_ANDROID_PLUGIN_ID
            }
        }
    }

    test("testStory2_composeModulesApplyKotlinComposeCompilerPlugin") {
        val detected = ToolchainBuildFiles.modulePaths.filter { module ->
            val applied = ToolchainBuildFiles.appliedPluginIds(module)
            applied.any { it.endsWith(".compose") && it.startsWith("pliuskis.") } ||
                ToolchainBuildFiles.moduleScript(module).readText().contains(Regex("""compose\s*=\s*true"""))
        }
        (composeModules + detected).distinct().forEach { module ->
            withClue("$module is a Compose module") {
                ToolchainBuildFiles.appliedPluginIds(module) shouldContain ToolchainBuildFiles.KOTLIN_COMPOSE_PLUGIN_ID
            }
        }
    }
})
