# Device discovery
**Last verified**: 2026-11-20 (kmp_build_ios_app STORY-5)
**Scope**: how the app finds IoT devices and tracks whether they are online (device list screen and the device firmware's MQTT birth/Last Will).

## Summary
Devices announce themselves on MQTT. On every (re)connect, a device publishes two **retained** messages: a JSON
description on `devices/{deviceId}/info` and `online` on `devices/{deviceId}/availability`. It also registers a
**Last Will** (`offline`, retained) on the availability topic, which the broker publishes if the device drops off
without a clean disconnect.

The app never asks "who is there?". It subscribes to `devices/+/info` and `devices/+/availability`; the broker
immediately replays every retained message, giving the full device list with online/offline state, and then streams
live changes. This is the same pattern as Home Assistant MQTT discovery and the Homie convention.

### How it works
| Topic | Retained | QoS | Published by | Payload |
|---|---|---|---|---|
| `devices/{deviceId}/info` | yes | 1 | device, on every connect | `{"name":"…","deviceId":"…","type":"plant"}`; an empty retained payload removes the device |
| `devices/{deviceId}/availability` | yes | 1 | device (`online` after connect, `offline` on clean shutdown); broker (`offline` Last Will) | `online` / `offline` |

1. App launch does not connect. When the device list screen collects its state, `DevicesViewModel` connects the shared
   HiveMQ client (`ConnectUseCase`). `IotManager.connect()` is a non-blocking `suspend` function, so the ViewModel
   calls it from `viewModelScope` without switching dispatcher; `HiveMqttClientManager` moves HiveMQ's own blocking
   connect call to `Dispatchers.IO` itself. The screen shows `Loading` meanwhile. An already connected client returns
   at once. A client that is still connecting or reconnecting is waited on (up to 10 s, with `withTimeoutOrNull` and
   `delay`, without a second connect) and counts as a failure if it doesn't connect in time. The client reconnects
   automatically and re-subscribes, after which the broker replays the retained snapshot again.
2. Only after the connection succeeds does `DevicesViewModel` collect `ObserveDevicesUseCase`. The data layer
   (`SubscribeDeviceTopics`) subscribes to both wildcard filters, takes `deviceId` from the topic, and emits
   `DeviceEvent.Info` / `DeviceEvent.Availability`.
3. `ObserveDevicesUseCase` folds the events into a registry of info and online state per `deviceId`. It emits the full
   list, sorted by name, after every event. State lives only in this fold; nothing is cached on disk.
4. `DevicesViewModel` maps the list to `DevicesListScreenState.Success`. If nothing arrives within 3 s of subscribing it
   emits an empty list, because MQTT has no "end of retained snapshot" marker. The timer starts after the connection
   succeeds, so a slow connect stays `Loading`. A device's card shows an online/offline indicator.
5. Tapping a device navigates to `control/{deviceId}`. The control route reads `deviceId` from the route and passes it
   to `ControlViewModel` (Koin `parametersOf`); status and commands use that same `deviceId`.

### Reconnects and foreground refresh
Discovery messages are retained, so after a reconnect the re-sent SUBSCRIBE brings the whole device list back on its
own. Status replies (`application/{applicationId}/{deviceId}/status`) are not retained: a reply sent while the app was
disconnected is lost. So the control screen asks again:
- **After an automatic reconnect** (Android and iOS). `IotManager.reconnects()` emits once for every CONNACK after the
  client's first connection, with no replay. HiveMQ reports it through `ReconnectListener`, a connected listener that
  `createMqttClient` adds to the client (`HiveMqttClientManager.reconnects()` finds it in the client's configuration);
  `IosMqttClientManager` emits it after re-sending the subscriptions. The domain sees it as the port `Reconnectable`
  (`ConnectMqttSource`), read by `ObserveReconnectsUseCase`. `ControlViewModel`, once its first connect succeeded,
  publishes one status request per reconnect for as long as it is alive.
- **When the iOS app returns to the foreground.** There is no MQTT in the background; iOS may close the socket while
  the app is suspended. `ForegroundRefresh` (`iosApp`) watches SwiftUI's `scenePhase` and calls the visible screen's
  refresh once each time the app comes back to `.active` from `.background` (also through `.inactive`, as after a
  lock), not on first launch and not after an `.inactive`-only trip (Control Center, an alert). A screen attaches it
  with `.refreshOnForeground { viewModel.refresh() }`; `LiveDeviceList` does so with `DevicesViewModel.refresh()`.
  `DevicesViewModel.refresh()` runs `retry()` from the error state and otherwise only connects (a connected client
  returns at once, a running attempt is waited on, so there is never a second CONNECT); the open discovery
  subscription is re-sent after the CONNACK and the broker replays the retained state, so a device that went offline
  meanwhile shows offline. `ControlViewModel.refresh()` connects the same way and then publishes a new status request.

Neither path ever publishes a water command; only the user's tap (`ControlViewModel.waterPlant`) does.

Error path: a malformed info payload is logged and skipped, and the other devices keep updating. The screen turns into
`Error` when the connection fails (an exception, or a broker that rejects the connection) or when the subscription
itself fails. A failed connection never subscribes. On `Error`, the device list shows the generic `ErrorScreen` from `core/designsystem`
with texts from string resources and a Retry button; the exception is logged, never shown. Retry runs
connect-then-discover again (`DevicesViewModel.retry`) and is ignored while a connect is still running, so repeated
taps start only one connect.

### iOS client
iOS uses the same topics and the same shared code from `ConnectUseCase` up; only the `IotManager` differs. The MQTT
socket is CocoaMQTT (Swift, MQTT 3.1.1, clean session, automatic reconnect), wrapped as `CocoaMqttTransport` in
`iosApp` and passed to `initShared` as the Kotlin `MqttTransport`. The transport only moves packets and reports
`onConnecting` / `onConnected` (CONNACK accepted) / `onDisconnected` / `onMessage`. `IosMqttClientManager`
(`core/data` iosMain) implements the `IotManager` contract on top:
- one connect attempt at a time: `connect()` on a connected client returns at once; while an attempt runs (a caller's
  or CocoaMQTT's automatic reconnect) it waits for that attempt, up to 10 s, and returns `FAILED` if it doesn't
  connect in time or the attempt ends in a disconnect;
- CocoaMQTT doesn't restore subscriptions on a clean session, so after every CONNACK the client sends SUBSCRIBE again
  for every open `subscribe` flow; the broker then replays the retained snapshot, as on Android;
- the transport reports each PUBLISH once, so the client matches the topic against the open filters (`+`, `#`) and
  decodes the payload as UTF-8;
- cancelling a collector sends one UNSUBSCRIBE for its filter (none while another open flow uses the same filter).

`NetworkConnectivity` on iOS is `IosNetworkConnectivity` over `NWPathMonitor`; `initShared` logs each change
("Pliuskis network: online" / "offline") to the unified log.

Broker settings on iOS: plaintext MQTT on port 1883 to the LAN broker (no TLS, no certificate in the app). The host is
`BROKER_HOST` of the active xcconfig, set per configuration in the gitignored `iosApp/Configuration/Secrets.xcconfig`
(`BROKER_HOST[config=Debug]`, `BROKER_HOST[config=Release]`; template `Secrets.template.xcconfig`): `localhost` on the
Simulator, the Mac's LAN IP (`ipconfig getifaddr en0`, the same value as `DEBUG_BROKER_HOST`) for a real iPhone or to
check Release against the LAN broker. The build writes it into the `BrokerHost` key of Info.plist, and
`BrokerConfiguration.settings(infoDictionary:clientId:)` (`iosApp`) reads it there; no source holds a host. A missing
host is empty, so the connect fails and the device list shows its error screen. Info.plist also has
`NSLocalNetworkUsageDescription` (no `NSBonjourServices`; there is no Bonjour). When the user denies Local Network
access, the socket never reaches the broker: the transport reports a disconnect, or nothing at all and the 10 s wait
ends, so `connect()` returns `FAILED` and the device list shows the error screen with retry; retry after allowing the
permission in Settings connects. The prompt never shows on the Simulator.

### iOS device list
The SwiftUI device list (`iosApp`) shows the same shared `DevicesViewModel` state; Swift only draws it. `RootView`
puts `LiveDeviceList` inside `DevicesNavigation` (a `NavigationStack`). `LiveDeviceList` owns `DevicesViewModel` and
`NetworkStatusViewModel` through `IosViewModelStoreOwner`, collects both while it is shown, and passes the current
values to the stateless `DeviceListScreen(state:isOffline:onRetry:)`:
- `Loading`: a progress indicator; `Success`: a `List` of rows in the order Kotlin sends them (sorted by name in
  `ObserveDevicesUseCase`; Swift never sorts), each with its type icon, name and Online/Offline; `Success` with no
  devices (also the 3 s timeout): "No devices found"; `Error`: "Couldn't load your devices" with a Retry button that
  calls `DevicesViewModel.retry()`. No timer or retry logic in Swift.
- The offline banner comes from `NetworkStatusViewModel.isOffline` (`shared` iosMain, `createNetworkStatusViewModel()`),
  the iOS counterpart of Android's app-level `PliuskisAppState.isOffline`: `NetworkConnectivity.isOnline` negated,
  `false` until the monitor reports. The banner sits above the list and doesn't replace it.
- The bottom toolbar shows a one-line status from the Kotlin state ("5 devices", "Looking for devices…", "Not
  connected"), marked `story1.kotlinValue` for STORY-1's launch UI test.
- A tapped row pushes `ControlRoute(deviceId:)`; `DevicesNavigation` opens the control screen it is given for that
  `deviceId` (`LiveControlScreen` in the app). The list's ViewModels stay alive while the control screen is on top.
- Accessibility identifiers: `deviceList.loading`, `deviceList.row.<deviceId>` (label the name, value
  Online/Offline), `deviceList.empty`, `deviceList.error`, `deviceList.retry`, `deviceList.offlineBanner`,
  `control.screen`, `control.deviceId`. UI tests drive the screen with fixed states through `-uiTestScenario` (Debug
  only, see `docs/testing.md`).

### iOS control screen
`LiveControlScreen` (`iosApp`) owns the shared `ControlViewModel` through `IosViewModelStoreOwner`
(`createControlViewModel(deviceId)`), collects `controlUiState` while it is shown and passes it to the stateless
`ControlScreen(deviceId:state:onWater:)`. Opening it connects if needed, publishes one status request and subscribes
to `application/{clientId}/{deviceId}/status`, all in Kotlin, as on Android. Back pops the screen; SwiftUI releases the
owner, which clears the ViewModel once, and the status subscription sends its UNSUBSCRIBE.
- `Loading`: a progress indicator. `Success`: soil humidity and water tank rows and the reading time, with the "Water
  plant" button below; thirsty and low values are orange. `Error`: "Couldn't get the status" (the message is not
  shown); there is no retry, the user goes back and opens the device again.
- The button exists only in `Success` and calls `ControlViewModel.waterPlant()` once per tap. As on Android there is no
  watering state: the screen doesn't change after a tap; the next status reply shows the new reading.
- `ControlStatusText` maps `HumidityStatus` and `WaterLevelStatus` to text and formats the reading time with
  `Date.FormatStyle` (abbreviated date, short time) for the environment's `\.locale` and `\.timeZone`. The thresholds
  stay in Kotlin (`DeviceStatusMapper`); Swift only switches over the enum values.
- `.refreshOnForeground { viewModel.refresh() }` asks for the status again after a return from the background; the
  Kotlin ViewModel also asks after every automatic reconnect (see "Reconnects and foreground refresh").
- Accessibility identifiers: `control.screen`, `control.deviceId` (the navigation bar title, label = deviceId, every
  state), `control.loading`, `control.humidity`, `control.waterLevel`, `control.updatedAt`, `control.water`,
  `control.error`.

## Key files
- Screen: `feature/devices/.../DeviceListScreen.kt`, `DeviceTypeIcon.kt` (device type to icon)
- ViewModel and UI state (shared): `core/presentation/.../devices/DevicesViewModel.kt`, `DevicesListScreenState.kt`,
  `DeviceListItem.kt`
- Error UI: `core/designsystem/.../component/ErrorScreen.kt`
- Use case: `core/domain/.../devicelist/ObserveDevicesUseCase.kt` (+ `DeviceEvent`, `Device`, `DeviceDomainModel`)
- Reconnects: `core/domain/.../connect/Reconnectable.kt`, `ObserveReconnectsUseCase.kt`;
  `core/data/.../mqttclient/ReconnectListener.kt` (Android); `ControlViewModel` (status request per reconnect,
  `refresh()`); `DevicesViewModel.refresh()`; `iosApp/iosApp/ForegroundRefresh.swift` (`refreshOnForeground`)
- Connect: `core/domain/.../connect/ConnectUseCase.kt`, `core/data/.../ConnectMqttSource.kt` (a rejected CONNACK throws)
- Data: `core/data/.../devices/SubscribeDeviceTopics.kt`, `core/data/.../mqttclient/HiveMqttClientManager.kt`, `MqttClientProducer.kt` (`createMqttClient`)
- iOS client: `iosApp/iosApp/CocoaMqttTransport.swift`; `core/data/src/iosMain/.../mqttclient/MqttTransport.kt`,
  `IosMqttClientManager.kt`, `TopicFilter.kt`; `.../util/IosNetworkConnectivity.kt`, `NwPathMonitorPathSource.kt`;
  `.../di/IosDataModule.kt`; `shared/src/iosMain/.../SharedEntryPoint.kt` (`initShared`), `NetworkStatusLog.kt`
- Broker settings: `app/.../broker/BrokerModule.kt` (debug or release `MqttSettings` from `BuildConfig`), `AssetKeystoreTlsSetup.kt`; `core/data/.../mqttclient/MqttSettings.kt`, `MqttTlsSetup.kt`
- Client ID: `core/data/.../clientid/ClientIdProvider.kt` (+ `ClientIdStore`, `SharedPreferencesClientIdStore`; iOS:
  `core/data/src/iosMain/.../clientid/UserDefaultsClientIdStore.kt`, `shared/src/iosMain/.../InstallClientId.kt`)
- iOS device list: `iosApp/iosApp/DeviceListScreen.swift`, `LiveDeviceList.swift`, `DevicesNavigation.swift`
  (`ControlRoute`), `RootView.swift`, `UITestScenario.swift` (Debug only);
  `shared/src/iosMain/.../NetworkStatusViewModel.kt` (offline banner state)
- iOS control screen: `iosApp/iosApp/LiveControlScreen.swift`, `ControlScreen.swift`, `ControlStatusText.swift`;
  `shared/src/iosMain/.../SharedEntryPoint.kt` (`createControlViewModel`)
- iOS broker settings: `iosApp/iosApp/BrokerConfiguration.swift`, `PliuskisApp.swift`; `iosApp/project.yml` (Info.plist
  keys); `iosApp/Configuration/Secrets.template.xcconfig` (`BROKER_HOST`)
- Firmware: `picow-pliuskis/main.py` (`mqtt_connect`, `connect_with_backoff`, ping in the main loop)

## Gotchas & decisions
- **`IotManager` is the contract for every platform client.** Its KDoc states it: `connect()` and `publishString()`
  suspend and never block the caller; `connect()` is safe when already connected or connecting; subscriptions survive
  a reconnect; `reconnects()` emits once per CONNACK after a lost connection. Status requests and the water command
  publish through the HiveMQ async API.
- **Each screen that uses MQTT connects for itself.** Nothing connects at app launch, and the splash screen doesn't wait
  for MQTT. `DevicesViewModel` connects before discovery, and `ControlViewModel` connects before it requests or
  subscribes to status. The control screen can't rely on the device list: after process death, navigation restores
  `control/{deviceId}` without composing the device list.
- **HiveMQ throws on a second connect.** `connect()` on a client that is already connected or connecting throws
  `MqttClientStateException`. `HiveMqttClientManager.connect` checks the client state first: connected returns at once,
  and connecting or reconnecting waits for that attempt. A concurrent connect that wins the race (the exception) is
  waited on the same way. So re-collecting the device list (back from background after the 5 s stop timeout) doesn't
  error, and a slow reconnect shows `Loading` and then the result instead of an early "No devices found".
- **The Last Will only fires on an ungraceful loss** (power cut, WiFi drop, missed keepalive). A clean `DISCONNECT`
  suppresses it, so the firmware publishes `offline` itself before disconnecting.
- **Keepalive needs pings.** `umqtt.simple` never sends PINGREQ on its own, so the firmware pings every
  `keepalive / 2` seconds. Without that, the broker drops an idle device after 1.5 × keepalive and marks it offline.
- **Offline is reported after up to 1.5 × keepalive** (90 s with the firmware's 60 s), not instantly, for losses that
  don't close the TCP connection.
- **Each app install has its own MQTT client ID.** On first launch `ClientIdProvider` generates a random ID of 23
  characters from `0-9a-zA-Z` and stores it in SharedPreferences; later launches reuse it, and a missing or invalid
  stored value is replaced by a new ID. iOS does the same with the common `ClientIdProvider` over
  `UserDefaultsClientIdStore` (standard UserDefaults); the app gets the ID from `installClientId()` and puts it in the
  `MqttSettings` it passes to `initShared`. Brokers drop a client when another connects with the same ID, so a shared ID
  would make two phones disconnect each other. The ID is also the `applicationId` in the status topic
  `application/{applicationId}/{deviceId}/status`, so the firmware treats it as an opaque string. Clearing the app's
  data or reinstalling (on iOS: deleting the app) gives a new ID.
- **The topic is the source of truth for `deviceId`.** The payload's `deviceId` is overwritten with the topic's, because
  command and status topics are built from it.
- **Removing a device** means publishing an empty retained payload to its `info` topic
  (`mosquitto_pub -r -n -t devices/{id}/info`). Clear `availability` the same way.
- **Unsubscribe on close.** `HiveMqttClientManager.subscribe` unsubscribes in `awaitClose`; otherwise every re-collect
  leaks a callback. Two collectors of the same topic filter at once would unsubscribe each other.
- **AWS IoT Core** supports retained messages and Last Will, but the device policy needs `iot:RetainPublish`, and the
  app policy needs `iot:Subscribe` on the `devices/+/…` topic filters plus `iot:Receive` on the matching topics.
  Because the client ID differs per install, the app policy must also allow `iot:Connect` for any client ID and the
  `application/{applicationId}/…` status topics for any `applicationId`.
- Every device is visible to every app; there is no per-user ownership yet.
