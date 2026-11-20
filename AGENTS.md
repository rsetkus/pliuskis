# AGENTS.md

Instructions for AI coding agents (Claude Code, Codex, Cursor, Copilot, Gemini and others) working in this repository.

## Build Commands

```bash
./gradlew assemble              # Build the project
./gradlew test                  # Run all unit tests
./gradlew detekt                # Detekt static analysis
./gradlew ktlintCheck           # Ktlint formatting check
./gradlew lint                  # Android lint
```

Run a single test class (in a shared KMP module, `--tests` goes on its `testAndroidHostTest` task; in an Android
module, on `testDebugUnitTest`):
```bash
./gradlew core:domain:testAndroidHostTest --tests "lt.setkus.pliuskis.core.systemstate.CommandGetSystemStateLevelUseCaseTest"
```

Run tests in a specific module:
```bash
./gradlew core:domain:test       # shared KMP module: runs its Android host tests
./gradlew core:domain:allTests   # shared KMP module: Android host and iOS simulator (macOS only)
./gradlew core:data:test
```

Build the iOS framework and run its tests (macOS with Xcode only):
```bash
./gradlew :shared:assembleSharedDebugXCFramework   # shared/build/XCFrameworks/debug/Shared.xcframework
./gradlew :shared:iosSimulatorArm64Test            # Kotlin tests of the Swift-facing API
# Swift tests against the XCFramework, from shared/ (list simulators: xcrun simctl list devices available):
xcodebuild test -scheme SharedSwiftTest -destination 'platform=iOS Simulator,name=iPhone 17'
```

Build and test the iOS app `iosApp/` (macOS with Xcode only; the Run Script phase builds `Shared` through Gradle, so
JDK 17 must be installed). Once per checkout, copy the settings template:
```bash
cp iosApp/Configuration/Secrets.template.xcconfig iosApp/Configuration/Secrets.xcconfig
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 17' build        # also -configuration Release
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
  -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build  # device (iosArm64) build, unsigned
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 17'               # unit and UI tests, Debug
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosAppUITests -configuration Release \
  -destination 'platform=iOS Simulator,name=iPhone 17'               # UI tests against the Release build
```

Release the iOS app to TestFlight (internal testers only) from GitHub Actions, never from a laptop (see § CI):
```bash
gh workflow run release-ios.yml --ref master   # or push a version tag: git tag v0.1 && git push origin v0.1
```

## CI

Every push runs `.github/workflows/build.yml` on JDK 17 (Temurin), the JDK that AGP 9.3 requires: `./gradlew assemble`, then `./gradlew test`, then
`./gradlew verifyPaparazziDebug` (screenshots against the committed goldens), then `./gradlew detekt ktlintCheck lint`,
stopping at the first failure. Pull requests also run the instrumented tests of every module (`./gradlew connectedCheck`
on an API 29 emulator, `build-ui.yml`). Run the same Gradle steps with JDK 17 before pushing; a newer local JDK can give
different results. See `docs/testing.md` for the Compose test setup.

The iOS checks run in `.github/workflows/build-ios.yml`, a macOS job on the `macos-26` runner: on every push to `master`,
and on a pull request only when it changes `core/**`, `shared/**`, `iosApp/**`, `build-logic/**` or `gradle/**` (a
Linux `changes` job reads the pull request's files; otherwise the macOS job shows as skipped, so a change to only
`feature/**`, `app/**`, `.github/**` or the root build files is first checked on `master`). It selects Xcode 26.4.1,
the Xcode that Kotlin 2.4.20-2.4.21 supports ([compatibility table](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html#version-compatibility));
bump `XCODE_APP` and `IOS_DESTINATION` in the workflow together with `kotlin`. It runs `iosSimulatorArm64Test` of
`core:domain`, `core:data`, `core:presentation` and `shared`, then `:shared:assembleSharedDebugXCFramework` and
`xcodebuild test -scheme SharedSwiftTest`, then starts Mosquitto on `localhost:1883` (Homebrew) for the broker contract
tests and runs `xcodebuild test` of scheme `iosApp` (unit and UI tests) on an iPhone 17 Simulator. It caches Gradle
(`gradle/actions/setup-gradle`) and `~/.konan`. Every job uploads its test reports when it fails (`android-test-reports`,
`android-ui-test-reports`, `ios-test-reports` with the `.xcresult` bundles).

The TestFlight release runs in `.github/workflows/release-ios.yml`, started by hand (workflow_dispatch) or by pushing a
version tag `v*`, never by a pull request. A Linux `guard` job fails for any ref other than `master` or a `v*` tag, so
the signing job never starts. The `testflight` job (`macos-26`, the same Xcode and JDK 17 as the iOS checks) uses the
GitHub environment `release` (a required reviewer; deployment refs `master` and `v*` tags), which holds the secrets
`APPLE_TEAM_ID`, `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_PRIVATE_KEY` (the text of the App Store Connect API key's `.p8`
file; Admin role) and `BROKER_HOST`; the repository has no secrets of its own. The job writes `Secrets.xcconfig` from its
template plus `DEVELOPMENT_TEAM` and `BROKER_HOST[config=Release]`, runs `xcodebuild archive` with cloud-managed signing
(`-allowProvisioningUpdates` with the API key) and `CURRENT_PROJECT_VERSION` set to the run number, then
`xcodebuild -exportArchive` with `iosApp/ExportOptions.plist`, which uploads the build to TestFlight for internal
testing only. The repository is public, so the job pins its actions to commit SHAs, prints no secret (xcodebuild
echoes the API key ID and issuer ID from its command line, masked as `***`), uploads no artifact and deletes the key
file and `Secrets.xcconfig` at the end. External TestFlight testing stays off until the login work (PLIUS-6) is done;
`Info.plist` answers export compliance with `ITSAppUsesNonExemptEncryption = NO`.

## Setup Requirements

- **JDK 17** required: the minimum for AGP 9.3 and Gradle 9.7, and the target of the build-logic convention plugins
- **AWS IoT credentials** — copy `default.secrets.properties` to `secrets.properties` and fill in:
  - `AMAZON_IOT_ENDPOINT`, `KEYSTORE_NAME`, `KEYSTORE_PASSWORD`, `CERT_ID`
  - The release build also needs the client keystore at `app/src/main/assets/legacy-keystore.p12` (gitignored)
- **Debug broker** — the debug build connects to `DEBUG_BROKER_HOST`:1883 (default `10.0.2.2`, the emulator's alias
  for the host). Set it in `secrets.properties` to a LAN address to run the debug build on a physical phone.

## Conventions - Kotlin / correctness
- Avoid `!!`, use smart-cast, `:?`. Every function should have singe exit `return`. For a real invariant use `checkNotNull`/`requireNotNull` with a message.

## Versions
Versions live in `gradle/libs.versions.toml`, referenced with `libs.X` notation. Never inline a version.

App version: the version name of both apps is `VERSION_NAME` in `version.properties` (repository root), and nowhere
else. `app/build.gradle.kts` reads it into `versionName`; `iosApp/Configuration/Base.xcconfig` includes the file and
sets `MARKETING_VERSION` (`CFBundleShortVersionString`) from it, so it keeps the `KEY = value` form that both Gradle and
Xcode read. The iOS build number `CURRENT_PROJECT_VERSION` (`CFBundleVersion`) is 1 locally and the workflow run number
in the release workflow.

Toolchain: the Gradle wrapper, AGP (`androidGradlePlugin`) and Kotlin (`kotlin`, the only Kotlin version entry) must
stay a set that the Kotlin Gradle plugin supports (see the compatibility table on kotlinlang.org). AGP 9 compiles Kotlin
itself (built-in Kotlin), so never apply `org.jetbrains.kotlin.android`. Compose modules get the Kotlin Compose compiler
plugin (`org.jetbrains.kotlin.plugin.compose`) from the `pliuskis.*.compose` convention plugins. `gradle.properties` sets
`android.newDsl=false` only because Paparazzi 1.x needs AGP's legacy extension types; remove it when Paparazzi moves to 2.x.

## Git / workflow
- Do not run git write operations (commit, push, branch) without an explicit request. Never commit to `main`/`master`.
- Match the branch upstream to the branch name: run `git checkout -b <b>`, then `git push -u origin <b>`. Never track `origin/main`/`origin/master`.
- Make no unsolicited changes. Do not refactor or reformat code you were not asked to touch. Mention it instead. Exception: an explicit "fix across the codebase" request.
- Do not build after small every small change. Build on request or at final verification. After a merge-conflict resolution, verify the affected before merge commit.

## Feature docs
Big cross-cutting flows live in `docs/features/`. Before you explore a flow, read its doc. Template: `docs/features/_TEMPLATE_.md`
**Update-on-touch**: when you change a document flow, or a module that has its own `AGENTS.md`, update that doc or file in the same change. Refresh its `Last verified` line too.
**`docs/` holds timeless reference only**. A doc describes how the app works today. It never describes work in progress. Do not a handoff note, a plan, a spec, an investigation write-up, a status report, or a migration checklist.

## Architecture

This is a Clean Architecture + MVVM Android app with Jetpack Compose UI, organized in a Gradle multi-module setup.

### Module Structure

```
app/                   — Entry point: MainActivity, Koin init, navigation host
core/
  domain/              — Use cases, domain models; shared KMP module (Android, iOS), no Android dependencies
  data/                — MQTT/IoT integration, network connectivity; shared KMP module: topics, payloads and the
                         `IotManager` contract in commonMain, the HiveMQ client and Android services in androidMain
  presentation/        — ViewModels, UI states and status threshold logic; shared KMP module, no Compose or
                         SavedStateHandle
  designsystem/        — Flora theme and shared Compose components (see docs/design-system.md)
feature/               — Android only: Compose UI, navigation, Koin wiring of the ViewModels, Android mapping
                         (display text, date format, icons); no ViewModel classes
  devices/             — Device list screen
  control/             — Device control screen
shared/                — iOS umbrella KMP module: builds the static framework `Shared` (iosArm64, iosSimulatorArm64)
                         with SKIE; Swift API `initShared(settings, transport)`, the ViewModel factories and `ScreenViewModels`
                         (iosMain)
iosApp/                — SwiftUI iOS app (iOS 17), not a Gradle module: links `Shared` through a Run Script phase
                         (`./gradlew :shared:embedAndSignAppleFrameworkForXcode`); no CocoaPods or KMMBridge, Swift
                         dependencies come from Swift Package Manager. `iosApp.xcodeproj` is generated from
                         `project.yml` with XcodeGen (`xcodegen generate` in `iosApp/`); commit both. Settings come
                         from `Configuration/*.xcconfig`; `Secrets.xcconfig` is gitignored, copy its template
                         `Secrets.template.xcconfig`. App sources in `iosApp/iosApp`, tests in `iosAppTests` (unit)
                         and `iosAppUITests` (UI)
build-logic/           — Gradle convention plugins (application, library, feature, compose, quality)
```

### Key Patterns

**Use Cases** — every business operation is a use case implementing `BaseUseCase<T, R>`. They return `Flow<Either<Throwable, R>>` using Arrow-kt for error handling.

**IoT/MQTT** — `IotManager` is the abstract interface (commonMain); `HiveMqttClientManager` is the HiveMQ implementation (androidMain). On iOS the socket is CocoaMQTT (Swift, SwiftPM, pinned in `iosApp/project.yml`): `CocoaMqttTransport` (`iosApp`) implements the Kotlin `MqttTransport` protocol and is passed to `initShared`; `IosMqttClientManager` (`core/data` iosMain, `internal`) implements `IotManager` over it and owns the contract logic (one connect attempt, the 10 s wait, re-subscribe after every CONNACK, unsubscribe on cancel, topic matching, UTF-8). `iosDataModule(transport)` binds it, and `NetworkConnectivity` to `IosNetworkConnectivity` over `NWPathMonitor`. `app`'s `brokerModule` picks the debug or production `MqttSettings` from `BuildConfig` and passes them to `core/data` through Koin; `core/data` reads no `BuildConfig`. Device communication is message-based via MQTT topics.

**Dependency Injection** — Koin; each module declares its own Koin module (e.g., `dataModule`, `devicesModule`), all loaded in `MyApplication`. A shared module splits its bindings into a common module and a platform module: `core/data` has `dataModule` (commonMain), `androidDataModule` (androidMain) and `iosDataModule` (iosMain); the app loads the first two, `initShared` the first and the last.

**State management** — ViewModels live in `core/presentation` (commonMain, the multiplatform `androidx.lifecycle.ViewModel`) and expose `StateFlow`; UI state sealed classes follow the `Loading / Success / Error` pattern (e.g., `DevicesListScreenState`). A ViewModel gets its arguments through its constructor (e.g. `ControlViewModel(deviceId, ...)`); the Android navigation layer reads the route argument and passes it with Koin `parametersOf`. UI state holds meanings, not display text (e.g. `HumidityStatus.THIRSTY`); each platform maps them to its own text.

**Swift API (`shared`)** — Swift calls `initShared(settings, transport)` once (it starts Koin; a second call does nothing),
with `MqttSettings` from `BrokerConfiguration` (host from `BROKER_HOST` of the xcconfig, via Info.plist) and the
per-install client ID from `installClientId()` (kept in UserDefaults), then
creates ViewModels with `createDevicesViewModel()`, `createControlViewModel(deviceId)` and
`createNetworkStatusViewModel()` (the offline banner state; Android keeps it app-level in `PliuskisAppState`). A SwiftUI screen owns its
ViewModels through `IosViewModelStoreOwner` (`iosApp`, held with `@StateObject`), which keeps them in a Kotlin
`ScreenViewModels` (an androidx `ViewModelStore`) and clears it when the screen goes away, so `onCleared` runs once. Swift never sees Koin, use
cases, `Either` or `IotManager`: `shared` exports only `core:presentation`; `MqttSettings` and `TlsMode` get exact
Swift names with `@ObjCName(exact = true)`; declarations Swift must not use are `internal` or `@HiddenFromObjC` (e.g.
`newControlViewModel`, which Android uses because the `ControlViewModel` constructor is `internal`). A Flow exposed to
Swift never throws: errors become an `Error` UI state. A `suspend` or throwing function added to the Swift API needs
`@Throws(..., CancellationException::class)`. SKIE (`skie` in `gradle/libs.versions.toml`) is upgraded together with
Kotlin.

### Testing Stack

- **Kotest** (FunSpec style) — primary unit test framework
- **Mockk** — mocking
- **Turbine** — Flow testing
- **Paparazzi** — Compose screenshot tests, on the JVM
- **Compose UI test** (`AndroidComposeTestRule`) — Compose behaviour tests, instrumented
- Unit tests live in `src/test/kotlin` (shared KMP modules: `src/commonTest/kotlin`), instrumented tests in
  `src/androidTest/kotlin`
- Details and commands: `docs/testing.md`

### Code Quality

Convention plugin `pliuskis.code.quality` applies Detekt and Ktlint to all modules. Kotlin style follows the official Kotlin code style (`kotlin.code.style=official` in `gradle.properties`).

## Related repos

The device firmware is `rsetkus/pico-pliuskis` (MicroPython on a Raspberry Pi Pico W). The app and the firmware only
meet through MQTT topics (see `docs/features/device-discovery.md`), so a topic or payload change must be made in both
repos.

## Agent files

`AGENTS.md` is the single source of instructions for every AI agent; edit only this file. `CLAUDE.md` (Claude Code)
and `.gemini/settings.json` (Gemini CLI) just point to it. Put tool-specific notes in those pointer files only when
they truly don't apply to other agents.
