# PRD: Pliuskis - Smart plant control system (`pliuskis`)
## Background
I am developing a mobile application that controls an IoT gardening device. It has two targets, **Android** and
**iOS**, built from one Kotlin Multiplatform codebase: the business logic, the MQTT client and the ViewModels are
shared, and each platform has its own native UI (Jetpack Compose on Android, SwiftUI on iOS).

This is v0.1 MVP application. It covers small separable features, device discovery and device control actions

## Goals
**Device discovery**
- A user can see IoT devices on the devices screen
- A user can select a device from the list on devices screen

**Control device**
- A user can open device details screen and read measurements from IoT
- A user can perform action by IoT device on details screen (water plant etc.)

---

## Non-goals (explicitly out of scope for v0.1)
- Design system
- Assets
- Multi-language / i18n
- Any configuration file — v0.1 should work with zero config
- Analytics

We will revisit any of these in v0.2 if v0.1 ships cleanly.

### Output
- Android: an executable archive (APK) to install on an Android device or emulator.
- iOS: an app for the iOS Simulator and, through TestFlight, for internal testers' iPhones (and Apple Silicon Macs,
  which run it as "Designed for iPhone").
- On both, the release build doesn't crash on launch.

---

## Technical notes
- Communication between application and IoT devices are performed over MQTT protocol

---

## Acceptance criteria (high-level — ProductOwner should refine into per-story testable AC)

The feature is done when:

**Building:**
- The release builds of both apps install and launch without errors (Android emulator or device; iOS Simulator or
  TestFlight).
- New build user-facing behaviors above work for their happy paths.
- New build edge case has either a passing test demonstrating the documented behavior, or a documented decision to defer (with reason).

**Across feature areas:**
- Tests cover each feature area's integration path end-to-end and the smaller components individually.
