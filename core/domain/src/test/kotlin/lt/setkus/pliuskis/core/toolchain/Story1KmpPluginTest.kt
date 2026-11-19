package lt.setkus.pliuskis.core.toolchain

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

/**
 * STORY-1 (kmp_extract_shared_library, PLIUS-12): the `pliuskis.kmp.library` convention plugin and the sample KMP
 * module `:core:kmpsample` (directory `core/kmpsample`) that proves it.
 *
 * These are the structural checks of AC1, AC2, AC4 and AC5. The Gradle outcomes (AC2 compile, AC3 tests on the
 * Android host and the iOS simulator, AC4 ktlint failure on a violation) are proved by running Gradle on the sample.
 */
class Story1KmpPluginTest : FunSpec({

    val root = ToolchainBuildFiles.repoRoot
    val kmpPluginId = "pliuskis.kmp.library"
    val codeQualityPluginId = "pliuskis.code.quality"
    val kotlinMultiplatformId = "org.jetbrains.kotlin.multiplatform"
    val androidKmpLibraryId = "com.android.kotlin.multiplatform.library"
    val ktlintId = "org.jlleitschuh.gradle.ktlint"
    val detektId = "io.gitlab.arturbosch.detekt"
    val sampleModule = ":core:kmpsample"
    val sampleDir = File(root, "core/kmpsample")
    val sampleScript = ToolchainBuildFiles.moduleScript(sampleModule)

    fun kmpPluginSource(): String {
        val source = ToolchainBuildFiles.conventionPlugins[kmpPluginId]
        return checkNotNull(source) { "$kmpPluginId is not registered in build-logic/convention/build.gradle.kts" }
    }

    // Shared helpers of the convention plugins (e.g. KotlinAndroid.kt), where the plugin may put its configuration.
    val buildLogicHelpers = ToolchainBuildFiles.buildLogicSources.filter {
        it.extension == "kt" && it.invariantSeparatorsPath.contains("/src/main/kotlin/lt/setkus/pliuskis/")
    }

    fun kmpConfigText(): String = (listOf(kmpPluginSource()) + buildLogicHelpers.map { it.readText() })
        .joinToString("\n")

    fun kotlinFilesIn(dir: File): List<File> =
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    // AC1

    test("testStory1_kmpLibraryPluginIdIsRegistered") {
        ToolchainBuildFiles.conventionPlugins.keys shouldContain kmpPluginId
    }

    test("testStory1_kmpLibraryPluginAppliesKotlinMultiplatformAndAndroidKmpLibrary") {
        val applied = ToolchainBuildFiles.pluginIdsReferencedIn(kmpConfigText())
        applied shouldContainAll listOf(kotlinMultiplatformId, androidKmpLibraryId)
    }

    test("testStory1_kmpLibraryPluginDoesNotApplyAndroidLibraryOrKotlinAndroid") {
        val applied = ToolchainBuildFiles.pluginIdsReferencedIn(kmpPluginSource())
        applied shouldNotContain "com.android.library"
        applied shouldNotContain ToolchainBuildFiles.KOTLIN_ANDROID_PLUGIN_ID
    }

    test("testStory1_kmpLibraryPluginDeclaresAndroidIosArm64AndIosSimulatorArm64Targets") {
        val text = kmpConfigText()
        withClue("android target (AGP KMP library DSL: android { } / androidLibrary { })") {
            Regex("""\bandroid(Library)?\s*\{|KotlinMultiplatformAndroidLibrary""").containsMatchIn(text) shouldBe true
        }
        withClue("iosArm64() target") { Regex("""\biosArm64\s*\(""").containsMatchIn(text) shouldBe true }
        withClue("iosSimulatorArm64() target") {
            Regex("""\biosSimulatorArm64\s*\(""").containsMatchIn(text) shouldBe true
        }
    }

    test("testStory1_kmpLibraryPluginEnablesAndroidHostTests") {
        kmpConfigText() shouldContain "withHostTest"
    }

    // AC2 (structure; the compile itself is proved by Gradle)

    test("testStory1_settingsIncludesKmpSampleModule") {
        ToolchainBuildFiles.modulePaths shouldContain sampleModule
    }

    test("testStory1_kmpSampleAppliesOnlyKmpLibraryPlugin") {
        withClue("${sampleScript.relativeTo(root).path} must exist") { sampleScript.isFile shouldBe true }
        val pluginsBlock = Regex("""plugins\s*\{([^}]*)}""").find(sampleScript.readText())?.groupValues?.get(1)
        val referenced = ToolchainBuildFiles.pluginIdsReferencedIn(pluginsBlock.shouldNotBeNull())
        referenced.toSet() shouldBe setOf(kmpPluginId)
    }

    test("testStory1_kmpSampleGetsMultiplatformPluginsThroughConvention") {
        val applied = ToolchainBuildFiles.appliedPluginIds(sampleModule)
        applied shouldContainAll listOf(kotlinMultiplatformId, androidKmpLibraryId)
        applied shouldNotContain "com.android.library"
        applied shouldNotContain ToolchainBuildFiles.ANDROID_APPLICATION_PLUGIN_ID
    }

    test("testStory1_kmpSampleHasCommonMainAndIosMainSources") {
        listOf("src/commonMain/kotlin", "src/iosMain/kotlin").forEach { dir ->
            withClue("core/kmpsample/$dir must hold Kotlin sources") {
                kotlinFilesIn(File(sampleDir, dir)).shouldNotBeEmpty()
            }
        }
    }

    // AC4 (structure; the ktlint failure on a planted violation is proved by Gradle)

    test("testStory1_kmpSampleAppliesKtlintAndDetektThroughConvention") {
        ToolchainBuildFiles.appliedPluginIds(sampleModule) shouldContainAll listOf(ktlintId, detektId)
    }

    test("testStory1_detektSourceListsCommonMainIosMainAndCommonTest") {
        val codeQuality = ToolchainBuildFiles.conventionPlugins[codeQualityPluginId].orEmpty()
        val text = kmpConfigText() + "\n" + codeQuality
        withClue("detekt must be configured in build-logic (not the module) with the KMP source directories") {
            text shouldContain "detekt"
            listOf("src/commonMain", "src/iosMain", "src/commonTest").forEach { dir -> text shouldContain dir }
        }
    }

    // AC5

    test("testStory1_kmpPluginAndSampleBuildFilesHaveNoInlineVersions") {
        val coordinateWithVersion = Regex(""""[\w.-]+:[\w.-]+:[0-9][^"]*"""")
        val versionCall = Regex("""\bversion\s*(\(|=)?\s*"[0-9][^"]*"""")
        val texts = mapOf(
            "$kmpPluginId implementation" to kmpPluginSource(),
            "build-logic/convention/build.gradle.kts" to
                File(root, "build-logic/convention/build.gradle.kts").readText(),
            "build.gradle.kts" to File(root, "build.gradle.kts").readText(),
            sampleScript.relativeTo(root).path to sampleScript.readText(),
        ) + buildLogicHelpers.associate { it.relativeTo(root).path to it.readText() }
        val offenders = texts.mapValues { (_, text) ->
            (coordinateWithVersion.findAll(text) + versionCall.findAll(text)).map { it.value }.toList()
        }.filterValues { it.isNotEmpty() }
        withClue("Inline versions (use gradle/libs.versions.toml): $offenders") { offenders.keys.shouldBeEmpty() }
    }

    test("testStory1_catalogEntriesTakeVersionsFromVersionsTable") {
        val catalog = ToolchainBuildFiles.catalog
        val inline = (catalog.plugins + catalog.libraries)
            .filterValues { it.inlineVersion != null && it.inlineVersion != "unspecified" }
            .keys
        withClue("Catalog entries with an inline version instead of version.ref") { inline.shouldBeEmpty() }
        val kmpPluginEntries = catalog.plugins.values.filter {
            it.coordinate == kotlinMultiplatformId || it.coordinate == androidKmpLibraryId
        }
        kmpPluginEntries.forEach { entry ->
            withClue("${entry.coordinate} version.ref must name a [versions] key") {
                catalog.versions.keys shouldContain entry.versionRef.shouldNotBeNull()
            }
        }
    }
})
