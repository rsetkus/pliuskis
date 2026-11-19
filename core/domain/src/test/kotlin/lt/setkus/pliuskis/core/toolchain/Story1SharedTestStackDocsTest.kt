package lt.setkus.pliuskis.core.toolchain

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import java.io.File

/**
 * STORY-1 AC6 (kmp_extract_shared_library, PLIUS-12): `docs/testing.md` has a section on shared (KMP) tests that names
 * the chosen shared test stack, keeps Turbine and kotlinx-coroutines-test, and gives the commands that run
 * `commonTest` on the Android host and on the iOS simulator.
 *
 * Whether the stated reason matches the Kotest FunSpec trial is a review check; copying the decision to PLIUS-12 is a
 * manual check.
 */
class Story1SharedTestStackDocsTest : FunSpec({

    val testingDoc = File(ToolchainBuildFiles.repoRoot, "docs/testing.md")

    /** The `##` section whose heading names shared, multiplatform or KMP tests, up to the next `##` heading. */
    fun sharedTestsSection(): String {
        val lines = testingDoc.readLines()
        val heading = Regex("""^##\s+.*\b(shared|multiplatform|kmp)\b.*""", RegexOption.IGNORE_CASE)
        val start = lines.indexOfFirst { heading.matches(it) }
        val section = if (start < 0) {
            ""
        } else {
            lines.drop(start + 1).takeWhile { !it.startsWith("## ") }.joinToString("\n")
        }
        return section
    }

    test("testStory1_testingDocHasSharedTestsSection") {
        withClue("docs/testing.md needs a '## ...' heading naming shared / multiplatform / KMP tests") {
            sharedTestsSection().shouldNotBeBlank()
        }
    }

    test("testStory1_testingDocNamesChosenSharedTestStack") {
        val section = sharedTestsSection()
        val namesStack = section.contains("Kotest") || section.contains("kotlin.test")
        withClue("The shared tests section must name Kotest FunSpec or the kotlin.test runner") {
            namesStack shouldBe true
        }
        section shouldContain "commonTest"
    }

    test("testStory1_testingDocKeepsTurbineAndCoroutinesTestInSharedStack") {
        val section = sharedTestsSection()
        section shouldContain "Turbine"
        section shouldContain "kotlinx-coroutines-test"
    }

    test("testStory1_testingDocGivesAndroidHostAndIosSimulatorTestCommands") {
        val section = sharedTestsSection()
        withClue("Android host test command, e.g. ./gradlew <module>:testAndroidHostTest") {
            section shouldContain "AndroidHostTest"
        }
        withClue("iOS simulator test command, e.g. ./gradlew <module>:iosSimulatorArm64Test") {
            section shouldContain "iosSimulatorArm64Test"
        }
    }
})
