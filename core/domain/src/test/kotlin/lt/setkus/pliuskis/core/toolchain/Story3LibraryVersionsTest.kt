package lt.setkus.pliuskis.core.toolchain

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.string.shouldNotContain

/**
 * STORY-3 AC19: the version catalog has KMP-ready library versions.
 *
 * Exact "latest stable" versions are not pinned here, because they go stale. Instead each library must be:
 * - a stable release (no `-alpha`, `-beta`, `-rc`, `-SNAPSHOT` suffix),
 * - newer than the version the project had before STORY-3,
 * - at or above a floor where a floor has a reason (cited next to it).
 * Whether each one is the newest stable release compatible with the STORY-2 Kotlin is a review check.
 *
 * Versions are found by Maven coordinate, not by version-key name, so a renamed key or a BOM entry still counts.
 */
class Story3LibraryVersionsTest : FunSpec({

    val catalog = ToolchainBuildFiles.catalog

    fun versionsOf(coordinatePrefix: String): List<String> {
        val versions = catalog.libraryVersions(coordinatePrefix)
        withClue("No versioned library with coordinate $coordinatePrefix* in libs.versions.toml") {
            versions.shouldNotBeEmpty()
        }
        return versions
    }

    fun assertAllAtLeast(coordinatePrefix: String, floor: String) {
        val versions = versionsOf(coordinatePrefix)
        withClue("$coordinatePrefix* versions $versions must be >= $floor") {
            versions.forEach { compareVersions(it, floor) shouldBeGreaterThanOrEqualTo 0 }
        }
    }

    fun assertAllNewerThan(coordinatePrefix: String, previous: String) {
        val versions = versionsOf(coordinatePrefix)
        withClue("$coordinatePrefix* versions $versions must be newer than the pre-STORY-3 $previous") {
            versions.forEach { compareVersions(it, previous) shouldBeGreaterThan 0 }
        }
    }

    // Pre-STORY-3 versions of the libraries AC19 names, from gradle/libs.versions.toml before the upgrade.
    val coroutines = "org.jetbrains.kotlinx:kotlinx-coroutines"
    val serialization = "org.jetbrains.kotlinx:kotlinx-serialization"
    val datetime = "org.jetbrains.kotlinx:kotlinx-datetime"
    val arrow = "io.arrow-kt:"
    val turbine = "app.cash.turbine:"
    val mockk = "io.mockk:"
    val navigation = "androidx.navigation:"
    val koin = "io.insert-koin:"
    val kotest = "io.kotest:"
    val lifecycle = "androidx.lifecycle:"
    val previousVersions = mapOf(
        coroutines to "1.7.3",
        serialization to "1.6.3",
        datetime to "0.3.2",
        arrow to "1.2.4",
        turbine to "1.0.0",
        mockk to "1.12.0",
        navigation to "2.7.4",
    )

    test("testStory3_koinIsAtLeast4_0") {
        assertAllAtLeast(koin, "4.0.0")
    }

    test("testStory3_kotestIsAtLeast6_0") {
        assertAllAtLeast(kotest, "6.0.0")
    }

    test("testStory3_lifecycleViewModelHasKmpVariant") {
        // AndroidX Lifecycle release notes, 2.8.0 (May 14, 2024), "ViewModel Kotlin Multiplatform Compatibility":
        // "The lifecycle-viewmodel artifact and APIs like ViewModel ... are now shipped in artifacts compatible with
        // Kotlin Multiplatform." lifecycle-viewmodel-compose became KMP in the same release.
        // https://developer.android.com/jetpack/androidx/releases/lifecycle#2.8.0
        assertAllAtLeast(lifecycle, "2.8.0")
    }

    test("testStory3_kotlinxDatetimeUsesKotlinTimeInstant") {
        // kotlinx-datetime 0.7.0 release notes: "Remove kotlinx.datetime.Instant and kotlinx.datetime.Clock in favor
        // of kotlin.time.Instant". https://github.com/Kotlin/kotlinx-datetime/releases/tag/v0.7.0
        assertAllAtLeast(datetime, "0.7.0")
    }

    test("testStory3_kotlinxSerializationIsBuiltForKotlin2_4") {
        // kotlinx.serialization 1.12.0 is the first release "based on Kotlin 2.4.10" (1.11.0 is based on 2.3.20).
        // https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.12.0
        assertAllAtLeast(serialization, "1.12.0")
    }

    test("testStory3_kotlinxCoroutinesIsBuiltForKotlin2") {
        // kotlinx.coroutines 1.9.0 release notes: "Kotlin was updated to 2.0". Earlier lines are built on Kotlin 1.9.
        // https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.9.0
        assertAllAtLeast(coroutines, "1.9.0")
    }

    test("testStory3_ac19LibrariesAreUpgradedFromPreviousVersions") {
        previousVersions.forEach { (coordinatePrefix, previous) -> assertAllNewerThan(coordinatePrefix, previous) }
    }

    test("testStory3_ac19LibrariesAreStableReleases") {
        val prefixes = previousVersions.keys + listOf(koin, kotest, lifecycle)
        prefixes.forEach { coordinatePrefix ->
            versionsOf(coordinatePrefix).forEach { version ->
                withClue("$coordinatePrefix* version $version must be a stable release") {
                    val lower = version.lowercase()
                    listOf("alpha", "beta", "-rc", ".rc", "snapshot", "-dev", "-m").forEach { marker ->
                        lower shouldNotContain marker
                    }
                }
            }
        }
    }
})
