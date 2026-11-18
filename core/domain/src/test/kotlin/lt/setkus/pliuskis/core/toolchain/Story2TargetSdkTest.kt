package lt.setkus.pliuskis.core.toolchain

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe

/**
 * STORY-2 AC13: targetSdk keeps the value it had before the story (34), because a change can alter runtime
 * behaviour. If a dependency forces a change, the story notes must name it and this pin moves with it.
 */
class Story2TargetSdkTest : FunSpec({

    val targetSdkBeforeStory = 34

    // `targetSdk = X`, `targetSdkVersion(X)` and the AGP 9 block form `targetSdk { version = release(X) }`
    val targetSdkPatterns = listOf(
        Regex("""targetSdk(?:Version)?\s*[=(]\s*([\w.]+)"""),
        Regex("""targetSdk\s*\{[^}]*release\(\s*([\w.]+)"""),
    )

    test("testStory2_targetSdkIsUnchanged") {
        val root = ToolchainBuildFiles.repoRoot
        val sources = ToolchainBuildFiles.buildLogicSources +
            ToolchainBuildFiles.modulePaths.map { ToolchainBuildFiles.moduleScript(it) }
        val constants = sources.flatMap { file ->
            Regex("""const\s+val\s+(\w+)\s*=\s*(\d+)""").findAll(file.readText())
                .map { it.groupValues[1] to it.groupValues[2].toInt() }
                .toList()
        }.toMap()
        val assignments = sources.flatMap { file ->
            targetSdkPatterns.flatMap { it.findAll(file.readText()) }
                .map { file.relativeTo(root).path to it.groupValues[1] }
                .toList()
        }

        assignments.shouldNotBeEmpty()
        assignments.forEach { (path, value) ->
            withClue("targetSdk = $value in $path") {
                (value.toIntOrNull() ?: constants[value]) shouldBe targetSdkBeforeStory
            }
        }
    }
})
