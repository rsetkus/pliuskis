# Testing conventions

## Framework

- Kotest, MockK and Junit5 for unit tests.
- Compose UI is the exception, see [Compose UI tests](#compose-ui-tests):
  - **Paparazzi** for rendering (screenshot tests on the JVM).
  - **Compose UI test** (`AndroidComposeTestRule`) for UI behaviour (instrumented).
- No other test framework.

## Story-scoped test naming (Sage convention)

Every test function name starts with the story it belongs to:

```kotlin
class Story1ParsesSimpleFrontmatterTest() : FunSpec({})

class Story3IndexSortsByDateDescTest() : FunSpec({})
```

Why: Sage's Tester runs story-scoped via.
```gradle
./gradlew :app:testDebugUnitTest --tests "Story<N>*"
```

If a test legitimately covers behavior owned by multiple stories (rare; almost only at the integration/e2e boundary), prefix with the higher-numbered story and name the others in the docstring.

## Test commands

- **Story-scoped (Tester worker default):** `./gradlew <module>:testDebugUnitTest --tests "Story<N>*"`
- **Full regression (EpicVerifier):** `./gradlew check`
- **Compose screenshots, story-scoped:** `./gradlew <module>:verifyPaparazziDebug --tests "*Story<N>*"`
- **Compose behaviour, story-scoped:** `./gradlew <module>:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.tests_regex=".*testStory<N>_.*"`

## Compose UI tests

Composables are tested in two ways. A screenshot test checks how a composable looks for a given state. A behaviour
test checks what happens when the user interacts with it. Don't test Compose layouts with plain unit tests.

Test classes for both kinds are JUnit 4. Test methods follow the story prefix: `testStory<N>_<description>`. Name the
class `Story<N><Subject>SnapshotTest` (Paparazzi) or `Story<N><Subject>Test` (behaviour). Detekt rejects class names
that start with a lowercase letter.

### Rendering: Paparazzi

[Paparazzi](https://github.com/cashapp/paparazzi) renders composables with LayoutLib on the JVM. It needs no emulator.

- **When:** one snapshot per visibly different state, such as loading, empty, error or content. For a design-system
  component, also snapshot it in the dark theme and at font scale 1.5.
- **Where:** in the module's `src/test/kotlin`. Golden images are written to `src/test/snapshots/images/`. Commit them.
- **How:**
  ```kotlin
  class Story1ErrorScreenSnapshotTest {
      @get:Rule
      val paparazzi = Paparazzi()

      @Test
      fun testStory1_showsTitleAndMessage() {
          paparazzi.snapshot {
              PliuskisTheme(darkTheme = false) {
                  Surface { ErrorScreen(title = "T", message = "M") }
              }
          }
      }
  }
  ```
  - Wrap content in `PliuskisTheme` and a `Surface`. Without a `Surface`, the snapshot has the default dark window and no
    themed background.
  - Pass `darkTheme` explicitly, so the snapshot doesn't depend on the machine.
  - Avoid running animations. At frame 0 they can render nothing; `LoadingWheel` does this.
  - For font scale or another device, pass `deviceConfig = DeviceConfig.PIXEL_5.copy(fontScale = 1.5f)` to
    `Paparazzi(...)`, one rule per configuration.
- **Commands:**
  - Record goldens: `./gradlew <module>:recordPaparazziDebug`
  - Compare against goldens: `./gradlew <module>:verifyPaparazziDebug`. A missing golden fails the run.
  - `testDebugUnitTest` (and so `./gradlew test`) runs snapshot tests but doesn't compare them. Use
    `verifyPaparazziDebug`.
- **CI:** `build.yml` runs `./gradlew verifyPaparazziDebug` on every push, so a snapshot that no longer matches its
  golden fails the build. On failure, the job uploads the diff images from `build/paparazzi/failures/` as the
  `paparazzi-failures` artifact.
- **Workflow:**
  1. TestCreator writes the snapshot tests.
  2. Developer implements the composable, then records the goldens.
  3. A human reviews the PNGs in the pull request before they become the reference.
  4. After any visual change, record again and review the diff.
- **Module setup:** see `core/designsystem/build.gradle.kts`.
  - Apply `alias(libs.plugins.paparazzi)`.
  - Add `testImplementation(libs.junit)` and `testRuntimeOnly(libs.junit.vintage.engine)`. Paparazzi is a JUnit 4 rule,
    and the vintage engine runs it on the JUnit Platform next to Kotest.
  - If the module also has tests that call `Dispatchers.setMain`/`resetMain` (ViewModel tests), run one JVM per test
    class: `tasks.withType<Test>().configureEach { setForkEvery(1L) }`, as in `feature/devices/build.gradle.kts`.
    After `resetMain`, `Dispatchers.Main` is unusable for the rest of that JVM, and later snapshots fail with "Module
    with the Main dispatcher had failed to initialize". Story-scoped runs hide this; it shows only when the whole suite
    runs together.
  - Copy the Guava `dependencies.constraints` block. Without it, LayoutLib fails with an `IllegalAccessError` on
    `Sets.toImmutableEnumSet` ([paparazzi#906](https://github.com/cashapp/paparazzi/issues/906)).
- **Version pin:** Paparazzi is pinned to 1.3.2, the last release that runs on AGP 8.1 and Gradle 8.0. Newer releases
  pull in a newer AGP and need Gradle 8.4 or later.
- **Jetifier:** `gradle.properties` lists `android.jetifier.ignorelist=android-base-common,common`, because Jetifier
  can't rewrite LayoutLib's `com.android.tools:common`.

### Behaviour: Compose UI test

- **When:** something has to happen in response to the user (a click invokes its callback), or a check on semantics is
  needed (an element is absent). Screenshots don't check behaviour.
- **Where:** in the module's `src/androidTest/kotlin`. These tests run on an emulator or a device.
- **How:**
  ```kotlin
  @RunWith(AndroidJUnit4::class)
  class Story1ErrorScreenTest {
      @get:Rule
      val composeTestRule = createAndroidComposeRule<ComponentActivity>()

      @Test
      fun testStory1_actionClickInvokesCallbackOnce() {
          var clicks = 0
          composeTestRule.setContent {
              ErrorScreen(title = "T", message = "M", actionLabel = "A", onAction = { clicks++ })
          }
          composeTestRule.onNodeWithText("A").performClick()
          assertEquals(1, clicks)
      }
  }
  ```
  - Match with semantic matchers first (`onNodeWithText`, `hasClickAction()`, `onNodeWithContentDescription`). Use a
    `testTag` only when a single node would need more than three matchers.
  - Read string resources through `composeTestRule.activity.getString(...)`.
  - A stateful composable also needs a state restoration test (`StateRestorationTester`).
- **Module setup:** see `core/designsystem/build.gradle.kts`.
  - `androidTestImplementation(libs.androidx.compose.ui.test.junit)`
  - `androidTestImplementation(libs.junit.ext)`
  - `debugImplementation(libs.androidx.compose.ui.test.manifest)`, which provides `ComponentActivity`.
  - Set `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` in `defaultConfig`.
  - A module that depends on `core:data` also needs `packaging.resources.excludes` for `META-INF/INDEX.LIST` and
    `META-INF/io.netty.versions.properties`, as in `feature/devices/build.gradle.kts`. HiveMQ's Netty jars each ship
    them, and the androidTest APK fails to package with duplicates.
- **CI:** `build-ui.yml` runs `./gradlew connectedCheck` on an API 29 emulator for every pull request. It covers the
  behaviour tests of every module. Run the same command locally, with an emulator or a connected device.


## What kinds of tests to write

- **Unit:** small pure functions in isolation (front-matter parsing, slug derivation, date sort).
- **Integration:** one layer's public API against a fixture (e.g. a use case against fake data source).
