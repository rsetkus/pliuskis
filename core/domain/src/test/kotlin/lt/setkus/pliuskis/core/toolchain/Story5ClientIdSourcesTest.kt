package lt.setkus.pliuskis.core.toolchain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.string.shouldNotContain
import java.io.File

private const val DEVICE_DISCOVERY_DOC = "docs/features/device-discovery.md"

/** The hardcoded client IDs and the fallback that STORY-5 removes. */
private val OLD_CLIENT_IDS = listOf("android-debug-client", "android-production-client", "default-client-id")

/**
 * Kotlin files of production source sets (`main`, `commonMain`, `androidMain`, `iosMain`, ...): every source set
 * under `src/` whose name doesn't contain "test" (`test`, `androidTest`, `commonTest`, `androidHostTest`, ...).
 */
private val mainKotlinSources: List<File> by lazy {
    ToolchainBuildFiles.moduleKotlinSources.filter { file ->
        val sourceSet = file.invariantSeparatorsPath.substringAfter("/src/").substringBefore('/')
        !sourceSet.contains("test", ignoreCase = true)
    }
}

/**
 * STORY-5 AC22 and AC25 (docs part): no hardcoded MQTT client ID is left in production code or in the device
 * discovery doc.
 */
class Story5ClientIdSourcesTest : FunSpec({

    // AC22
    // GIVEN the main sources of every module
    // WHEN searching for the old client IDs
    // THEN none of android-debug-client, android-production-client, default-client-id is found
    test("testStory5_mainSourcesHaveNoHardcodedClientIds") {
        mainKotlinSources.shouldNotBeEmpty()

        val offenders = mainKotlinSources.flatMap { file ->
            val text = file.readText()
            OLD_CLIENT_IDS.filter { text.contains(it) }
                .map { "${file.relativeTo(ToolchainBuildFiles.repoRoot).invariantSeparatorsPath}: $it" }
        }

        offenders.shouldBeEmpty()
    }

    // AC25 (docs part)
    // GIVEN docs/features/device-discovery.md
    // THEN it doesn't mention the old client IDs
    test("testStory5_deviceDiscoveryDocHasNoOldClientIds") {
        val doc = File(ToolchainBuildFiles.repoRoot, DEVICE_DISCOVERY_DOC).readText()

        OLD_CLIENT_IDS.forEach { doc shouldNotContain it }
    }
})
