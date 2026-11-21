# Device status
**Last verified**: 2026-11-21 (kmp_ci_cd_release STORY-4 / PLIUS-32)
**Scope**: how the control screen of one device (Android and iOS) asks the device for its status and shows soil moisture, the water tank and the reading time.

## Summary
The control screen shows one device's latest reading: whether the plant is fine or thirsty, the soil moisture and the
water tank as percentages, and when the device took the reading. Status replies are not retained on the broker, so
the app has to ask: it publishes a status request with its own client ID, and the device answers on a topic that only
this app install subscribes to. The device keeps answering for a while after one request (see the firmware's
`docs/features/status-and-watering.md`), so an open screen updates on its own.

Both apps run the same shared code from the ViewModel down (`ControlViewModel`, the use cases, the MQTT topics and
the payload decoding). The thresholds that turn numbers into meanings (`THIRSTY`, `LOW`) and the percentages are
computed in Kotlin; each platform only turns them into its own words and date format. The look (health card, reading
tiles, colors) is the Flora design, see `docs/design-system.md`.

### How it works
| Topic | Retained | QoS | Published by | Payload |
|---|---|---|---|---|
| `devices/{deviceId}/status` | no | 1 | app, to ask | `{"applicationId":"<the app's client ID>"}` |
| `application/{applicationId}/{deviceId}/status` | no | 1 | device, to answer | `{"humidity":<float>,"waterLevel":<float>,"timestamp":{"epochSeconds":<long>,"nanosecondsOfSecond":<int>}}` |

`humidity` and `waterLevel` are on the firmware's 0..10 scale (10 = wet or full). `applicationId` is the per-install
MQTT client ID (`ClientIdProvider`, see `device-discovery.md`), so each phone gets its own replies.

1. The user taps a device in the list. Android opens it in the detail pane of the list-detail layout
   (`DevicesListDetail`; on a phone the detail covers the list, from 840dp both show side by side). The app's
   `PliuskisNavHost` fills that pane with `ControlPane(deviceId, deviceName, …)`, which creates `ControlViewModel`
   through Koin, one per device: `koinViewModel(key = "control-<id>") { parametersOf(deviceId) }`. iOS selects
   `ControlRoute(deviceId:deviceName:)` in `DevicesNavigation` (a `NavigationSplitView`: a stack on iPhone, side by
   side on iPad); `LiveControlScreen` owns the ViewModel through `IosViewModelStoreOwner`
   (`createControlViewModel(deviceId)` of `shared`). The device name from the list is only the title ("Plant" while it
   is empty, e.g. after process death on Android). The state starts as `ControlUiState.Loading`.
2. `ControlViewModel` connects first (the `connect` function it gets, `ConnectUseCase` underneath; a connected client
   returns at once). The screen can open without the device list having connected, e.g. after process death on
   Android, where the pane navigator restores the selected device.
3. Once connected, it publishes one status request (`RequestSystemStateUseCase` → `AwsSystemStateRequest`, topic
   `devices/{deviceId}/status`). In parallel, while the screen collects `controlUiState`, it subscribes to the reply
   topic (`GetSystemStatusUseUseCase` → `SubscribeSystemStatusTopic`).
4. Each reply is decoded into `SystemState` (`humidity`, `waterLevel`, `timestamp` as `kotlin.time.Instant`) and
   mapped by `DeviceStatusMapper` to `DeviceStatus`: a value below 5 is `HumidityStatus.THIRSTY` /
   `WaterLevelStatus.LOW`, 5 and above `GOOD` / `ENOUGH`; `soilMoisturePercent` and `waterLevelPercent` are the
   readings × 10, rounded and clamped to 0..100. The state becomes `ControlUiState.Success(DeviceStatus)`, and every
   later reply replaces it.
5. The platform draws it, with the same words on both:
   - Health card: "Feeling great" / "Soil moisture is just right." (`GOOD`) or "Thirsty" / "The soil is dry. Water
     soon." (`THIRSTY`), in Leaf or Clay, and "Updated <time>": only the time for a reading from today, day, month and
     time for an older one, in the phone's time zone and locale.
   - "Conditions": two tiles, "Soil moisture" (percentage, meter, "Healthy" or "Dry") and "Water tank" (percentage,
     meter, "Full enough" or "Refill soon").
   - The "Water now" button pinned at the bottom (`watering.md`).
   - Android (`feature/control`): `ControlScreen` in `ControlPane`, strings in `res/values/strings.xml`, the time from
     `UpdatedAtFormatter`. iOS (`iosApp`): `ControlScreen(deviceName:state:wateringState:onWater:onRetry:)`, the words
     and the time from `ControlStatusText` (`Date.FormatStyle` for the environment's `\.locale` and `\.timeZone`).
     The iOS accessibility identifiers are listed in `device-discovery.md`, "iOS control screen".
6. The ViewModel asks again after every automatic reconnect of the MQTT client (`ObserveReconnectsUseCase`), and on
   iOS when the app comes back from the background (`refreshOnForeground { viewModel.refresh() }`). Details in
   `device-discovery.md`, "Reconnects and foreground refresh".
7. Leaving the screen clears the ViewModel; the reply subscription is cancelled and sends its UNSUBSCRIBE.

Error path: a failed connect gives `ControlUiState.Error` and nothing is published or subscribed. A reply that does
not decode (not JSON, a missing or an unknown field) also gives `Error`, and it ends the reply subscription. Both apps
show "Can't reach your plant" / "Check your internet connection and try again." with a "Retry" button; the error
message is never shown (Android logs it). Retry calls `ControlViewModel.retry()`, which works only from `Error`: it
connects again (`Loading` meanwhile), then publishes a new status request and subscribes again. A device that never
answers leaves the screen in `Loading`; there is no timeout.

State lives only in the ViewModel's `controlUiState` (a `StateFlow`); nothing is cached on disk.

## Key files
- ViewModel and UI state (shared): `core/presentation/.../control/ControlViewModel.kt` (`ControlUiState`, `retry`,
  `refresh`, `newControlViewModel`), `DeviceStatus.kt` (`HumidityStatus`, `WaterLevelStatus`), `DeviceStatusMapper.kt`
  (thresholds, percentages)
- Use cases: `core/domain/.../systemstate/RequestSystemStateUseCase.kt`, `GetSystemStatusUseUseCase.kt`,
  `SystemState.kt` (wire model), ports `SystemStateRequestable.kt`, `SystemStateSubscribable.kt`;
  `core/domain/.../di/KoinModules.kt` (`coreModule`)
- Data: `core/data/.../systemstate/AwsSystemStateRequest.kt` (request topic and payload),
  `SubscribeSystemStatusTopic.kt` (reply topic), `SystemUpdateMessageModel.kt`; `core/data/.../di/KoinModules.kt`
  (`dataModule`)
- Android screen: `feature/control/.../ControlScreen.kt` (`ControlPane`, `ControlScreen`, `PlantStatus`),
  `UpdatedAtFormatter.kt`, `KoinModule.kt` (`controlModule`), `res/values/strings.xml`;
  `feature/devices/.../DevicesListDetail.kt` (detail pane); `app/.../navigation/PliuskisNavHost.kt`
- Android components: `core/designsystem/.../component/StatusTile.kt`, `ErrorScreen.kt`, `PliuskisLoading.kt`,
  `PliuskisTopAppBar.kt`
- iOS screen: `iosApp/iosApp/LiveControlScreen.swift`, `ControlScreen.swift`, `ControlStatusText.swift`,
  `DevicesNavigation.swift` (`ControlRoute`), `ForegroundRefresh.swift`, `Flora.swift`;
  `shared/src/iosMain/.../SharedEntryPoint.kt` (`createControlViewModel`)
- Tests: `core/presentation/src/commonTest/.../control/` (mapper, connection, reconnect, retry);
  `feature/control/src/test/.../ControlScreenSnapshotTest.kt`, `UpdatedAtFormatterTest.kt`;
  `iosApp/iosAppTests/Story5ControlStatusTextTests.swift`, `iosApp/iosAppUITests/Story5ControlScreenUITests.swift`
- Firmware (`rsetkus/pico-pliuskis`): `lib/plant.py` (`Plant.on_message`, `tick`, `status_json`), `main.py`
  (`publish_status`) and its `docs/features/status-and-watering.md`

## Gotchas & decisions
- **The topics and the payload are a contract with the firmware.** Change them in both repos together
  (`rsetkus/pico-pliuskis`).
- **Decoding is strict.** `GetSystemStatusUseUseCase` uses the default `Json`, which rejects unknown fields, and
  `epochSeconds` must be an integer. The firmware sends exactly `humidity`, `waterLevel` and `timestamp`.
- **One bad reply stops the updates until Retry.** The decode error ends the subscription flow and shows the same
  "Can't reach your plant" screen as a failed connect. Retry subscribes again (and so does, on Android, a screen that
  stops collecting for more than 5 s and starts again).
- **The first reply can be missed.** The request and the subscription start in parallel after the connect, so the
  device may answer before the SUBSCRIBE reaches the broker. The firmware's periodic replies cover it.
- **The app doesn't repeat the request on a timer.** It asks on open, after each reconnect, after Retry and (iOS) on
  return from the background. The firmware keeps replying for a limited time after a request
  (`STATUS_SUBSCRIPTION`), so a screen left open longer stops updating.
- **The thresholds and percentages are in Kotlin only.** Compose and Swift switch over the enum values and show the
  percentages they get; a new threshold goes into `DeviceStatusMapper`, with its test.
- **One ViewModel per device.** Android keys it by `control-<deviceId>` and iOS gives each route its own screen
  (`.id(deviceId)`), so switching devices in a two-pane layout never mixes their state.
- **The device has no water-level sensor**: the firmware sends a fixed `waterLevel` (`WATER_LEVEL`, 10), so the
  water tank always shows 100% and "Full enough".
- **The reply topic contains the client ID.** It must stay free of `/`, `+` and `#`, which `ClientIdProvider`
  guarantees (`0-9a-zA-Z`).
