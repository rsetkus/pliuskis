# Pliuškis

Pliuškis controls a smart plant pot: a Raspberry Pi Pico W with a soil-moisture sensor and a pump
([rsetkus/pico-pliuskis](https://github.com/rsetkus/pico-pliuskis)). The app lists the devices on an MQTT broker,
shows a device's soil humidity and water level, and waters the plant on request.

There are two apps, built from one Kotlin Multiplatform codebase:
- **Android**: Jetpack Compose UI (`app`, `feature/*`, `core/designsystem`).
- **iOS**: SwiftUI app in `iosApp/` (iOS 17), on top of the `Shared` framework built by the `shared` module.

Both use the same shared modules: use cases (`core/domain`), the MQTT client and topics (`core/data`) and the
ViewModels (`core/presentation`). The apps and the firmware meet only through MQTT topics.

## Build and test

Requirements: JDK 17, the Android SDK, and on macOS Xcode for the iOS app. Setup, every build and test command, and
the CI and TestFlight release workflows are in [AGENTS.md](AGENTS.md) (§ Setup Requirements, § Build Commands, § CI).
In short, from a fresh clone:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # macOS; any JDK 17 elsewhere
export ANDROID_HOME=$HOME/Library/Android/sdk       # or sdk.dir in local.properties
cp default.secrets.properties secrets.properties
./gradlew assemble test                             # Android app, and the shared modules' Android host tests

# iOS (macOS with Xcode only)
cp iosApp/Configuration/Secrets.template.xcconfig iosApp/Configuration/Secrets.xcconfig
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 17' build
```

## Docs

- [AGENTS.md](AGENTS.md): architecture, conventions, setup, commands and CI.
- [docs/testing.md](docs/testing.md): the test setup of the Android, shared (KMP) and iOS code, and how to run it.
- [docs/features/](docs/features/): how the main flows work, on both apps (device discovery, device status,
  watering).
- [docs/design-system.md](docs/design-system.md): the Flora design of both apps (colors, type, layout, components).
- [PRD.md](PRD.md): the product requirements of the v0.1 MVP.
- [staging/README.md](staging/README.md): a broker with simulated devices, to run the apps without a Pico.
