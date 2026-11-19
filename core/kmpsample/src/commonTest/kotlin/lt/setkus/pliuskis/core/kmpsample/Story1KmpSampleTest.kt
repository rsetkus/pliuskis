package lt.setkus.pliuskis.core.kmpsample

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank

/**
 * STORY-1 AC3 (kmp_extract_shared_library, PLIUS-12): one common test that runs on the Android host and on the iOS
 * simulator (`./gradlew :core:kmpsample:allTests`).
 *
 * Production code it needs, in `core/kmpsample`:
 * - `src/commonMain/kotlin`: `expect fun platformName(): String` and
 *   `fun greeting(): String = "Hello, ${platformName()}!"`
 * - `src/androidMain/kotlin` and `src/iosMain/kotlin`: the `actual fun platformName()` of each platform.
 *
 * Written as a Kotest FunSpec, the stack AC6 tries first. If FunSpec cannot run on iosSimulatorArm64, AC6 falls back to
 * the kotlin.test runner with Kotest assertions; then this class becomes a kotlin.test class with the same two checks
 * and the same `testStory1_` names.
 */
class Story1KmpSampleTest : FunSpec({

    test("testStory1_platformNameIsNotBlank") {
        platformName().shouldNotBeBlank()
    }

    test("testStory1_greetingNamesThePlatform") {
        greeting() shouldBe "Hello, ${platformName()}!"
    }
})
