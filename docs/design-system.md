# Pliuškis Flora design system
**Last verified**: 2026-11-21
**Scope**: the visual language of both apps: colors, type, shape, spacing, icons, layout and components. On Android it
lives in `core/designsystem` (Material 3, Compose material3 1.3, adaptive 1.1). On iOS it lives in
`iosApp/iosApp/Flora.swift` and the asset catalog's color sets (SwiftUI, Liquid Glass on iOS 26). The section after
each rule names the Android and iOS pieces.

## Summary
Pliuškis looks after a plant. The UI should feel calm and natural, like a garden notebook rather than an
industrial dashboard. It answers one question at a glance: *is my plant OK?* The plant's state leads every screen,
the numbers come next, and the action sits at the bottom where the thumb rests.

Both apps share one design: the same palette, layout, words and states. Each draws it with its own platform's
components, so Android feels like Material 3 and iOS feels like iOS.

Principles:
- **State first.** The health card on the control screen is the first thing to read. Its color alone says healthy
  or thirsty.
- **Values over labels.** A reading shows a large number ("72%") and a small label ("Soil moisture"), never the
  other way round.
- **Color means something.** Each hue has one job (see below). Never use a hue for decoration.
- **Light first, dark complete.** People check plants in daylight, so the light theme is the default design. Dark
  follows the system setting and is equally finished.
- **Brand colors always.** There is no dynamic (wallpaper) color, so "green = healthy" holds on every device.
- **Same meaning, native form.** The shared ViewModels (`core/presentation`) decide every state; each platform only
  draws it.

## Color

| Hue | Light / dark | Means | Android role | iOS color set |
|---|---|---|---|---|
| Leaf | `#2D6A3F` / `#96D5A4` | Healthy, online, positive; the app tint | `primary`, `primaryContainer` | `Leaf`, `LeafContainer`, `OnLeafContainer`, `AccentColor` |
| Rain | `#2A6589` / `#97CEF7` | Water: the water action, the water tank | `secondary` | `Rain`, `OnRain` |
| Clay | `#8C4F2C` / `#FFB68F` | Needs attention soon: dry soil, low tank | `tertiary`, `tertiaryContainer` | `Clay`, `ClayContainer`, `OnClayContainer` |
| Red | system red | Something failed: connection or command | `error` | `.red` |
| Neutrals | slightly green | Pages, cards, text | `surface`, `surfaceContainer*`, `on*` | `Canvas`, `Card`, `Track`, system label colors |

- **Attention is Clay, not red.** A dry plant is a normal, fixable state. Red is only for failures.
- **Pairs only:** text and icons use the matching "on" color of their container. Never fade text with alpha; it
  breaks contrast.
- **Android:** Material 3 tonal surfaces. The page is `surface` (`PliuskisBackground`). Rows and tiles use
  `surfaceContainerHigh`, and a selected row in two-pane mode uses `secondaryContainer`. There are no shadows. The XML
  window theme (`app/src/main/res/values*/themes.xml`) mirrors the canvas and primary, so the first frame matches.
  Screens use `MaterialTheme.colorScheme.*`, never raw `Color(...)`.
- **iOS:** `Flora.*` reads the color sets (`Assets.xcassets`, each with a dark variant). The page is `Canvas`, a
  grouped-style background, with white `Card` rows and tiles on it, as iOS grouped lists do. The tint is Leaf.

## Typography
Each platform uses its own system font and type scale. Screens pick roles and never set sizes themselves. Aim for
four sizes or fewer per screen.

| Use | Android (Roboto, M3 scale) | iOS (SF Pro, Dynamic Type) |
|---|---|---|
| Reading value ("72%") | `displaySmall`, tabular figures | `.largeTitle` rounded, `.monospacedDigit()` |
| Health card title | `headlineSmall` | `.title2` semibold |
| Screen title | Large top app bar (`headlineMedium` → `titleLarge`) | Large navigation title, inline on the control screen |
| Device name in a row | `bodyLarge` (`ListItem` headline) | `.body` |
| Section header ("Conditions") | `titleSmall` | `.footnote` semibold, uppercase (grouped-list style) |
| Captions, status, buttons | `labelLarge` / `labelMedium` | `.subheadline` / `.headline` |

## Shape
- **Android:** the Material 3 default scale (4/8/12/16/28dp). Rows and tiles use `shapes.medium` (12dp), the health
  card uses `shapes.extraLarge` (28dp), and buttons are full pills.
- **iOS:** continuous rounded rectangles. Tiles are 16 pt (`Flora.Radius.tile`) and the health card is 28 pt
  (`Flora.Radius.healthCard`). Buttons are capsules, and list rows use the system inset-grouped shape.

## Spacing and touch
The same 4/8 scale on both platforms: `xxs` 4, `xs` 8, `sm` 12, `md` 16, `lg` 24. Android adds `xl` 32 and `xxl`
48 (`theme/Spacing.kt`); iOS uses `Flora.Spacing`.
- Screen margin 16. Inside a tile 16; inside the health card 24. Between tiles 12. Between sections 24.
- Related items sit closer than unrelated ones. A title and its message are 4–8 apart; separate sections are 24
  apart.
- Touch targets are at least 48dp on Android and 44 pt on iOS. The water button is the largest control (56dp; iOS
  `.controlSize(.large)`).

## Icons
- **Android:** `icon/PliuskisIcons.kt`, all from the Material **Rounded** set. Icons in `material-icons-core` are used
  as they are; others are copied into `icon/RoundedIcons.kt` from `material-icons-extended`. Don't add the extended
  library itself: it is large enough to make test APK builds run out of Gradle heap.
- **iOS:** SF Symbols: `leaf.fill` (plant), `humidity.fill`, `drop.fill`, `drop.degreesign.slash` (thirsty),
  `checkmark.circle.fill`, `wifi.slash`.
- On both, an icon is decorative (no label) when text next to it says the same thing.

## Liquid Glass (iOS 26)
Glass belongs to controls and navigation, never to content. The deployment target stays iOS 17, so every custom
use is gated with `#available(iOS 26, *)` and falls back to materials (`Flora.swift`: `floraGlassCapsule`,
`floraProminentButton`).
- The navigation bar, the bottom toolbar (the list summary) and the split view's sidebar are system chrome. They turn
  to glass on their own when built with the iOS 26 SDK.
- **"Water now"** uses `.buttonStyle(.glassProminent)` tinted Rain, with `OnRain` text. Before iOS 26 it is
  `.borderedProminent` in a capsule.
- **Empty and error actions** ("Search again", "Retry") use the same prominent style, tinted Leaf.
- **The offline banner** is a glass capsule tinted with ClayContainer. Before iOS 26 it is a thin material capsule.
- Content stays opaque: the health card, the reading tiles and the list rows.

## Components

| Component | Android | iOS |
|---|---|---|
| Page background | `PliuskisBackground` | `Flora.canvas` |
| Top-level title | `PliuskisLargeTopAppBar` (collapses on scroll) | `.navigationTitle` (large title) |
| Detail title + back | `PliuskisTopAppBar` (pinned, tints on scroll) | Inline navigation title (`control.title`) |
| Reading | `StatusTile` (`StatusTone`: Positive, Informative, Attention) | `ReadingTile` in `ControlScreen.swift` |
| Empty / error screen | `InfoScreen` / `ErrorScreen` | `ContentUnavailableView` with a prominent action |
| Loading | `PliuskisLoading` (M3 `CircularProgressIndicator`) | `ProgressView` with a label |
| Offline | `OfflineBanner` (full width, `tertiaryContainer`) | Glass capsule above the list |

- A reading is read by screen readers as one item ("Soil moisture, 30%, Dry").
- An empty state is an invitation with a next step, never a bare line of grey text.
- Never show an exception message; it is only logged.

## Layout
Both apps use a list-detail layout:
- **Android:** `ListDetailPaneScaffold` (`feature/devices/.../DevicesListDetail.kt`). On compact and medium windows
  one pane shows at a time, the control screen slides over the list, and predictive back returns to it. On expanded
  windows (840dp and up) both panes show side by side. Detail content stops growing at 840dp and stays centered.
- **iOS:** `NavigationSplitView` (`DevicesNavigation.swift`). On iPhone it collapses into a stack. On iPad the list is
  the sidebar and the selected device is the detail.
- With nothing selected on a wide screen, the detail shows "Choose a device".

## Screen patterns
- **Device list ("Your garden"):** one row per device: a symbol in a tinted circle (Leaf while online), the name, and
  Online or Offline. Offline rows use neutral colors instead of fading, so they stay readable. A count summary ("3
  devices · 2 online") shows above the rows on Android and in the bottom toolbar on iOS. The empty state is "No
  devices yet" with **Search again**; the error is "Couldn't load your devices" with **Retry**. Both call
  `DevicesViewModel.retry()`.
- **Control screen:**
  - The device name is the title. Below it is the health card: Leaf "Feeling great" or Clay "Thirsty", with
    "Updated 23:40" (only the time today, the day and time otherwise).
  - Then "Conditions": two readings, soil moisture (Leaf, or Clay when dry) and water tank (Rain, or Clay when low).
    Each shows a percentage, a meter and a caption.
  - "Water now" in Rain is pinned to the bottom. Its feedback follows `ControlViewModel.wateringState`:
    - "Watering…" with a progress indicator
    - "Watering started" with a check, back to "Water now" after 3 s (iOS adds a success haptic)
    - "Try again" with an error line above it on failure
  - The health card's colors animate when the status changes. An error shows "Can't reach your plant" with
    **Retry** (`ControlViewModel.retry()`).

## Copy
Both apps use the same words. Sentence case, short, and plain. Use "your" for the user's things ("Your garden").
Errors say what happened and what to do ("Couldn't reach your plant. Try again."). Use the ellipsis only for
something in progress ("Watering…"). No exclamation marks or emoji in system text.

## Gotchas & decisions
- **Compose BOM 2025.06.01 (Compose 1.8, material3 1.3) is the ceiling while compileSdk is 35.** Newer Compose needs
  compileSdk 36 or 37, and Paparazzi 1.x fails on those `android.jar`s (see `build-logic` `Constants.kt`).
- **One `ControlViewModel` per device:** on Android it is keyed `control-<id>` in the garden destination's ViewModel
  store; on iOS `DevicesNavigation` gives each device its own screen (`.id(deviceId)`), so its owner and ViewModel
  are new. On Android, switching devices in two-pane mode keeps earlier ViewModels until the garden destination
  leaves the back stack.
- **The device name comes from the list.** On Android, after process death the detail can come back before
  discovery finishes, so the title briefly shows "Plant".
- **Snapshot tests render on `PliuskisBackground`**, as the app does, so the goldens show the real UI. Paparazzi
  doesn't report window size to the adaptive library, so list-detail snapshots pass an explicit `scaffoldDirective`.
- **Sensor scale:** the firmware reports soil moisture and water level on a 0..10 scale (10 = wet / full).
  `DeviceStatusMapper` turns both into whole percentages for both apps. The firmware has no tank sensor yet and
  always reports a full tank.
