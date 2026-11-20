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
- **Version pin:** Paparazzi stays on 1.3.2. No stable release supports the AGP 9 DSL: 1.x looks up AGP's legacy
  `TestedExtension`, which AGP 9 registers only with `android.newDsl=false` (set in `gradle.properties`), and the 2.x
  line that supports AGP 9 is still alpha and needs JDK 21. Moving to 2.x means removing `android.newDsl=false` and
  re-recording the goldens.
- **compileSdk ceiling:** Paparazzi 1.x renders with the API 34 LayoutLib and copies `android.os.Build` from the
  module's `android.jar`. From compileSdk 36 that jar has `Build.VERSION_CODES_FULL`, which LayoutLib lacks, so every
  snapshot fails with `NoSuchElementException` in `Renderer.configureBuildProperties`. `COMPILE_SDK` stays at 35
  (`build-logic/.../Constants.kt`), which also caps AndroidX libraries that need compileSdk 36+ (Lifecycle 2.11,
  Navigation 2.10, Koin 4.2 through Activity 1.12). Lift both together when Paparazzi moves to 2.x.
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


## Shared (KMP) module tests

A shared module applies `pliuskis.kmp.library` (`build-logic/.../KmpLibraryConventionPlugin.kt`), plus any Kotlin
compiler plugin its code needs, such as `kotlin.serialization`. The plugin gives it the targets Android, `iosArm64` and
`iosSimulatorArm64`, the Android host tests (`withHostTest`), a `test` task that runs them, the test dependencies
below, and ktlint and detekt over `src/commonMain`, `src/androidMain`, `src/iosMain` and the test source sets.
`core/domain`, `core/data` and `core/presentation` are shared modules.

- **Stack:** Kotest `FunSpec` with Kotest assertions, the same as the Android modules, plus Turbine for Flows and
  kotlinx-coroutines-test (`runTest`). The plugin adds all four to `commonTest`. Tests that will run on iOS don't use
  MockK; MockK is JVM-only. Write a fake instead.
- **Why Kotest:** a Kotest `FunSpec` in `commonTest` runs on both targets. On iOS, the Kotest Gradle plugin (`io.kotest`)
  and KSP (`com.google.devtools.ksp`) generate the test entry point. The Android host test runs it on the JUnit
  Platform through `kotest-runner-junit5`. So the kotlin.test runner isn't needed. The Kotest docs say the native
  engine has fewer features than the JVM engine; for example, annotation-based configuration doesn't work on iOS.
- **Where:** in `src/commonTest/kotlin`. Use `src/androidHostTest/kotlin` or `src/iosTest/kotlin` only for tests of
  platform code (`androidMain`/`iosMain`, e.g. the HiveMQ client in `core/data`); those may use MockK on the Android
  host, and they see the `commonTest` fakes. Test names follow the story prefix: class `Story<N><Subject>Test`, tests
  `testStory<N>_...`. detekt treats `androidHostTest` as test code, like `src/test`.
- **ViewModel tests** (`core/presentation`) are in `commonTest` too and run on both targets. Swap the Main dispatcher
  with `Dispatchers.setMain(StandardTestDispatcher())` and `resetMain()` from kotlinx-coroutines-test, create the
  ViewModel through a `ViewModelStore` with `ViewModelProvider.create(store, factory)[X::class]`, and clear the store
  before `resetMain()`. A ViewModel test that needs the HiveMQ client stays a JVM test in the feature module
  (`feature/devices/src/test`).
- **Commands:**
  - Android host: `./gradlew <module>:test` or `./gradlew <module>:testAndroidHostTest`. `--tests` works only on
    `testAndroidHostTest` (`test` is a lifecycle task that runs it), and only with a fully qualified class name, e.g.
    `--tests "lt.setkus.pliuskis.data.di.Story8DataKoinGraphTest"`. With Kotest, a wildcard such as `Story<N>*` finds
    no tests, and `*.ClassName` runs every test of the module.
  - iOS simulator (macOS with Xcode only): `./gradlew <module>:iosSimulatorArm64Test`
  - Both: `./gradlew <module>:allTests`. The reports are in `<module>/build/test-results/testAndroidHostTest` and
    `<module>/build/test-results/iosSimulatorArm64Test`.
- **CI:** the CI runners are Linux, and Kotlin/Native can't build or run iOS targets there, so it skips them. iOS
  tests run only on macOS. The plugin's `test` task depends on `testAndroidHostTest`, so CI's `./gradlew test` runs
  the Android host tests of every shared module, as it runs the unit tests of the Android modules.
- **Generated sources:** KSP writes the Kotest entry points to `build/generated/ksp`. The plugin keeps `build/` out
  of ktlint, so generated code isn't linted.

### The `shared` module and Swift tests

`shared` builds the iOS framework `Shared` and holds the Swift-facing API (`initShared`, the ViewModel factories) in
`src/iosMain`. It is tested on two levels, both on macOS only:

- **Kotlin:** `src/iosTest/kotlin` calls the API as Swift does (start the graph with `initShared`, create a ViewModel
  through a factory, collect its state). Koin is process-wide, so each test calls `stopKoin()` afterwards. The tests
  pass `initShared` a fake `MqttTransport` whose broker refuses every connection, so connecting fails and screens end
  in their Error state. Run: `./gradlew :shared:iosSimulatorArm64Test`.
- **Swift:** XCTest cases in `shared/swift-test/SharedSwiftTests` run against the built XCFramework through the Swift
  package `shared/Package.swift` (scheme `SharedSwiftTest`). Koin starts once per process, so only one Swift test may
  call `initShared` with its own settings. Run:
  ```bash
  ./gradlew :shared:assembleSharedDebugXCFramework
  cd shared
  xcodebuild test -scheme SharedSwiftTest -destination 'platform=iOS Simulator,name=iPhone 17'
  ```
  Pick an installed simulator with `xcrun simctl list devices available`. The static framework needs the C++ runtime,
  so the test target links `c++`. Swift 6 language mode needs `extension <ViewModel>: @retroactive @unchecked
  Sendable {}` before a test awaits a ViewModel's async API.
- **Header check:** the generated header `Shared.framework/Headers/Shared.h` must not name Koin, use cases, `Either`,
  `IotManager` or HiveMQ types:
  ```bash
  for h in shared/build/XCFrameworks/debug/Shared.xcframework/*/Shared.framework/Headers/Shared.h; do
    grep -nE 'Either|Koin|UseCase|IotManager|Hive|Mqtt3' "$h"; done   # prints nothing
  ```
  KDoc of exported declarations is copied into the header, so the check also catches these names in comments.
- **CI:** none of this runs on the Linux runners; `./gradlew test` there runs the module's (empty) Android host tests.

## iOS app tests (`iosApp`)

The SwiftUI app in `iosApp/` has two XCTest targets, both on macOS with Xcode only. Neither runs in CI (the runners
are Linux).

- **Unit (`iosAppTests`):** Swift logic of the app, e.g. `IosViewModelStoreOwner`. A hosted test bundle with
  `@testable import iosApp` and `import Shared`. It compiles against `Shared` but doesn't link it
  (`-disable-autolink-framework Shared`): the Kotlin classes come from the host app, so the process has one Kotlin
  runtime. A test can subclass a Kotlin class such as `Lifecycle_viewmodelViewModel` in Swift. `CocoaMQTT` is
  importable in the bundle the same way (a package dependency with `link: false` in `project.yml`).
- **Broker contract (`iosAppTests`):** tests that drive `CocoaMqttTransport` against a real broker (e.g.
  `Story2MqttBrokerContractTests`) need the staging Mosquitto on `localhost:1883` (`docker compose up -d --build` in the
  staging project). Without it they fail with the start command; they don't skip.
- **UI (`iosAppUITests`):** XCUITest against the launched app. Find elements by accessibility identifier
  (`.accessibilityIdentifier(...)` in the app).
- **UI test scenarios:** a screen test that needs a fixed state launches the app with `-uiTestScenario <name>`
  (`app.launchArguments += ["-uiTestScenario", name]`). `RootView` reads it from the standard UserDefaults (launch
  arguments land in its argument domain) and shows `UITestScenarioView` instead of the live ViewModels: the real
  screen inside the real `DevicesNavigation`, fed with fixed Kotlin state objects (e.g. `DevicesListScreenStateSuccess`
  with `DeviceListItem`s, `ControlUiStateSuccess` with a `DeviceStatus`) and fake callbacks, plus test controls outside
  the navigation stack (`uiTestScenario.retryCount`, `uiTestScenario.goOnline`, `uiTestScenario.waterCount`,
  `uiTestScenario.controlClearedCount`, `uiTestScenario.nextControlState`). The control scenarios (`control*`) open a
  control screen that owns a Swift `Lifecycle_viewmodelViewModel` subclass through `IosViewModelStoreOwner`, so a
  test sees `onCleared` run on Back. Scenarios are in `iosApp/iosApp/UITestScenario.swift` inside `#if DEBUG`, so the Release
  build has none; a test that uses them skips itself in Release with `#if !DEBUG ... throw XCTSkip(...)`. Add a case
  to `UITestScenario` for a new state.
- **Naming:** class `Story<N><Subject>Tests`, methods `testStory<N>_<description>`.
- **Setup:** once per checkout, `cp iosApp/Configuration/Secrets.template.xcconfig iosApp/Configuration/Secrets.xcconfig`.
  Each build runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode`, which needs JDK 17 (found with
  `/usr/libexec/java_home -v 17` when `JAVA_HOME` is not set).
- **Commands** (pick a simulator with `xcrun simctl list devices available`):
  ```bash
  # Unit and UI tests, Debug
  xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosApp \
    -destination 'platform=iOS Simulator,name=iPhone 17'
  # UI tests against the Release build (the unit tests need @testable, which Release doesn't enable)
  xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -configuration Release \
    -destination 'platform=iOS Simulator,name=iPhone 17'
  # Story-scoped
  xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosApp \
    -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:iosAppTests/Story1ViewModelStoreOwnerTests
  ```
- **Simulator architecture:** Gradle builds `Shared` for `iosSimulatorArm64` only, so `project.yml` excludes x86_64
  from Simulator builds.

## What kinds of tests to write

- **Unit:** small pure functions in isolation (front-matter parsing, slug derivation, date sort).
- **Integration:** one layer's public API against a fixture (e.g. a use case against fake data source).
