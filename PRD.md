# PRD: Pliuskis - Smart plant control system (`pliuskis`)
## Background
I am developing Android application that controls IoT gardening device.

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
Executable archive to install on Android device/emulator, release build non-crashing on launch.

---

## Technical notes
- Communication between application and IoT devices are performed over MQTT protocol

---

## Acceptance criteria (high-level — ProductOwner should refine into per-story testable AC)

The feature is done when:

**Building:**
- release builds installs and launches on emulator or device without errors.
- New build user-facing behaviors above work for their happy paths.
- New build edge case has either a passing test demonstrating the documented behavior, or a documented decision to defer (with reason).

**Across feature areas:**
- Tests cover each feature area's integration path end-to-end and the smaller components individually.
