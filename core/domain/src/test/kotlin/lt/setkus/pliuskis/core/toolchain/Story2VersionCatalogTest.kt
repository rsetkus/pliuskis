package lt.setkus.pliuskis.core.toolchain

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.nulls.shouldNotBeNull

/**
 * STORY-2 AC11: the version catalog names Kotlin >= 2.4.0 and AGP >= 9.0.0, has one Kotlin version entry and no
 * standalone Compose compiler version, and the Gradle wrapper is new enough for that AGP.
 */
class Story2VersionCatalogTest : FunSpec({

    val catalog = ToolchainBuildFiles.catalog

    fun kotlinVersions(): List<String> {
        val pluginVersions = catalog.plugins.values
            .filter { it.coordinate.startsWith("org.jetbrains.kotlin.") }
            .mapNotNull { catalog.resolveVersion(it) }
        val libraryVersions = catalog.libraries.values
            .filter { it.coordinate.startsWith("org.jetbrains.kotlin:") }
            .mapNotNull { catalog.resolveVersion(it) }
        return pluginVersions + libraryVersions
    }

    fun agpVersion(): String? = catalog.plugins.values
        .firstOrNull { it.coordinate == ToolchainBuildFiles.ANDROID_APPLICATION_PLUGIN_ID }
        ?.let { catalog.resolveVersion(it) }

    test("testStory2_kotlinVersionIsAtLeast2_4_0") {
        val versions = kotlinVersions()
        withClue("Kotlin versions in the catalog: $versions") {
            versions.shouldNotBeEmpty()
            versions.forEach { compareVersions(it, "2.4.0") shouldBeGreaterThanOrEqualTo 0 }
        }
    }

    test("testStory2_catalogHasSingleKotlinVersionEntry") {
        catalog.versions shouldNotContainKey "kotlinVersion"
        val kotlinRefs = (catalog.plugins.values.filter { it.coordinate.startsWith("org.jetbrains.kotlin.") } +
            catalog.libraries.values.filter { it.coordinate.startsWith("org.jetbrains.kotlin:") })
            .map { it.versionRef ?: "inline:${it.inlineVersion}" }
            .distinct()
        withClue("Kotlin plugins and libraries must all use one version entry, found $kotlinRefs") {
            kotlinRefs shouldHaveSize 1
        }
    }

    test("testStory2_catalogHasNoStandaloneComposeCompilerVersion") {
        catalog.versions shouldNotContainKey "androidxComposeCompiler"
        val composeCompilerLibraries = catalog.libraries.values.filter {
            it.coordinate.startsWith("androidx.compose.compiler:")
        }
        composeCompilerLibraries.shouldBeEmpty()
    }

    test("testStory2_androidGradlePluginIsAtLeast9_0_0") {
        val agp = agpVersion().shouldNotBeNull()
        withClue("AGP version $agp") { compareVersions(agp, "9.0.0") shouldBeGreaterThanOrEqualTo 0 }
        val agpLibrary = catalog.libraries.values.firstOrNull { it.coordinate == "com.android.tools.build:gradle" }
        val agpLibraryVersion = agpLibrary?.let { catalog.resolveVersion(it) }.shouldNotBeNull()
        withClue("build-logic AGP dependency $agpLibraryVersion") {
            compareVersions(agpLibraryVersion, "9.0.0") shouldBeGreaterThanOrEqualTo 0
        }
    }

    test("testStory2_gradleWrapperIsSupportedByAgp9") {
        // AGP 9.0 needs Gradle 9.1.0 or newer. Later AGP minors need newer Gradle; the full build (AC14) catches that.
        val gradle = ToolchainBuildFiles.wrapperGradleVersion
        withClue("Gradle wrapper $gradle") { compareVersions(gradle, "9.1.0") shouldBeGreaterThanOrEqualTo 0 }
    }
})
