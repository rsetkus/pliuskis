# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew assemble              # Build the project
./gradlew test                  # Run all unit tests
./gradlew detekt                # Detekt static analysis
./gradlew ktlintCheck           # Ktlint formatting check
./gradlew lint                  # Android lint
```

Run a single test class:
```bash
./gradlew core:domain:test --tests "lt.setkus.pliuskis.core.systemstate.CommandGetSystemStateLevelUseCaseTest"
```

Run tests in a specific module:
```bash
./gradlew core:domain:test
./gradlew core:data:test
```

## Setup Requirements

- **JDK 17** required (enforced by build-logic convention plugins)
- **AWS IoT credentials** — copy `default.secrets.properties` to `secrets.properties` and fill in:
  - `AMAZON_IOT_ENDPOINT`, `KEYSTORE_NAME`, `KEYSTORE_PASSWORD`, `CERT_ID`

## Conventions - Kotlin / correctness
- Avoid `!!`, use smart-cast, `:?`. Every function should have singe exit `return`. For a real invariant use `checkNotNull`/`requireNotNull` with a message.

## Versions
Versions live in `gradle/libs.versions.toml`, referenced with `libs.X` notation. Never inline a version.

## Git / workflow
- Do not run git write operations (commit, push, branch) without an explicit request. Never commit to `main`/`master`.
- Match the branch upstream to the branch name: run `git checkout -b <b>`, then `git push -u origin <b>`. Never track `origin/main`/`origin/master`.
- Make no unsolicited changes. Do not refactor or reformat code you were not asked to touch. Mention it instead. Exception: an explicit "fix across the codebase" request.
- Do not build after small every small change. Build on request or at final verification. After a merge-conflict resolution, verify the affected before merge commit.

## Feature docs
Big cross-cutting flows live in `docs/features/`. Before you explore a flow, read its doc. Template: `doc/features/_TEMPLATE_.md`
**Update-on-touch**: when you change a document flow, or a module that has its own `CLAUDE.md`, update that doc or file in the same change. Refresh its `Last verified` line too.
**`docs/` holds timeless reference only**. A doc describes how the app works today. It never describes work in progress. Do not a handoff note, a plan, a spec, an investigation write-up, a status report, or a migration checklist.

## Architecture

This is a Clean Architecture + MVVM Android app with Jetpack Compose UI, organized in a Gradle multi-module setup.

### Module Structure

```
app/                   — Entry point: MainActivity, Koin init, navigation host
core/
  domain/              — Use cases, domain models (no Android dependencies)
  data/                — MQTT/IoT integration, network connectivity
  designsystem/        — Shared Material 3 Compose components and theming
feature/
  devices/             — Device list screen and ViewModel
  control/             — Device control screen and ViewModel
build-logic/           — Gradle convention plugins (application, library, feature, compose, quality)
```

### Key Patterns

**Use Cases** — every business operation is a use case implementing `BaseUseCase<T, R>`. They return `Flow<Either<Throwable, R>>` using Arrow-kt for error handling.

**IoT/MQTT** — `IotManager` is the abstract interface; `HiveMqttClientManager` is the HiveMQ implementation. `MqttClientProducer` selects debug vs production client based on `BuildConfig.DEBUG`. Device communication is message-based via MQTT topics.

**Dependency Injection** — Koin; each module declares its own Koin module (e.g., `dataModule`, `devicesModule`), all loaded in `MyApplication`.

**State management** — ViewModels expose `StateFlow`; UI state sealed classes follow the `Loading / Success / Error` pattern (e.g., `DevicesListScreenState`).

### Testing Stack

- **Kotest** (FunSpec style) — primary unit test framework
- **Mockk** — mocking
- **Turbine** — Flow testing
- Unit tests live in `src/test/kotlin`, instrumented tests in `src/androidTest/kotlin`

### Code Quality

Convention plugin `pliuskis.code.quality` applies Detekt and Ktlint to all modules. Kotlin style follows the official Kotlin code style (`kotlin.code.style=official` in `gradle.properties`).
