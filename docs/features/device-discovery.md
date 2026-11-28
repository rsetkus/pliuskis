# Device discovery
**Last verified**: 2026-11-25 (kmp_cognito_login STORY-2 / PLIUS-35; release builds connect to AWS IoT over a SigV4-signed WebSocket with the signed-in user's Cognito identity)
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
   succeeds, so a slow connect stays `Loading`. A device's row shows Online or Offline (Flora: see
   `docs/design-system.md`). An empty list shows "No devices yet" with "Search again", which calls
   `DevicesViewModel.retry()`. `retry` works from `Error` and from an empty `Success`, and is ignored while devices
   are shown.
5. Tapping a device opens its detail in the list-detail layout (Android `DevicesListDetail.kt`, a
   `ListDetailPaneScaffold`). The app fills the detail with the control feature's `ControlPane(deviceId, deviceName,
   …)`, which creates `ControlViewModel` with Koin (`koinViewModel(key = "control-<id>") { parametersOf(deviceId) }`).
   Status and commands use that `deviceId`; the name, taken from the list, is only the title. The pane navigator saves
   the selected id, so it survives process death.

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
`Error` when the connection fails (an exception, a socket error, no credentials from Cognito) or when the subscription
itself fails. A broker that answers but refuses the signed-in identity (`IotAccessDeniedException`, see
[The release connection](#the-release-connection-aws-iot-over-cognito)) is not an error: the list is empty. A failed connection never subscribes. On `Error`, the device list shows the generic `ErrorScreen` from
`core/designsystem` with texts from string resources and a Retry button; the exception is logged, never shown. Retry runs
connect-then-discover again (`DevicesViewModel.retry`) and is ignored while a connect is still running, so repeated
taps start only one connect.

### The release connection: AWS IoT over Cognito
Release builds of both apps connect to the prod stack's AWS IoT with the signed-in user's Cognito identity (sign-in:
[sign-in.md](sign-in.md)); no certificate is in either app. Debug builds sign in too (dev stack), but MQTT stays on the
LAN broker: plaintext TCP on port 1883, the 23-character install client ID, no AWS credentials and no signing.

- **Settings.** `MqttSettings` with `TlsMode.WEBSOCKET_SIGV4`: the host is the AWS IoT endpoint (`AMAZON_IOT_ENDPOINT`),
  the port 443, and the client ID the install suffix; `connectionTarget` is `wss://<endpoint>:443/mqtt`. The identity
  pool, region and endpoint are `AwsIotSettings` (Android: `brokerModule` from `COGNITO_IDENTITY_POOL_ID`, `AWS_REGION`,
  `AMAZON_IOT_ENDPOINT` of `secrets.properties`; iOS: `initSharedOverAwsIot` from the Info.plist keys
  `CognitoIdentityPoolId`, `AwsRegion`, `AmazonIotEndpoint`). Android release declares no `MqttTlsSetup`.
- **Credentials.** `AwsIotCredentialsCache` (`core/data` commonMain) gets the identity ID (`GetId`) once and temporary
  credentials (`GetCredentialsForIdentity`, about 1 hour) with the user pool's ID token, over plain HTTPS
  (`CognitoIdentityClient`). Before every attempt (`clientIdForAttempt`) it gets new credentials when the cached ones
  expire within 5 minutes; a refused ID token is refreshed once with the refresh token. Signed out, it makes no Cognito
  call and no attempt is made; signing out also closes the open connection, so nothing reconnects as the old identity.
- **A new URL on every connect and reconnect.** `signAwsIotWebSocketUrl` (commonMain, the same code on both apps)
  signs `wss://<endpoint>/mqtt` with SigV4 (service `iotdevicegateway`, `X-Amz-SignedHeaders=host`) at the time of the
  attempt, and appends `X-Amz-Security-Token` after the signature, the only form AWS IoT accepts. Every query
  value is percent-encoded, so the token's `+`, `/` and `=` are `%2B`, `%2F`, `%3D`. AWS IoT closes a SigV4
  connection after 24 hours at most; the clients' automatic reconnects then sign a new URL with new credentials.
  - iOS: CocoaMQTT's WebSocket asks `AwsIotWebSocketUrlProvider` for the URL of every attempt, its own automatic
    reconnects included (`PerAttemptWebSocketConnectionBuilder`).
  - Android: HiveMQ reconnects with the same transport settings, so `AwsIotMqttClientManager` makes HiveMQ's
    reconnects itself through its reconnector and signs a new URL before each one. HiveMQ escapes a `%` in its
    WebSocket query again (`%2B` becomes `%252B`; AWS IoT answers 403, and also to a raw `+`), so HiveMQ connects in
    plain WebSocket to `SignedWebSocketRelay` inside the app (127.0.0.1, a one-time path per attempt), which opens TLS
    to the endpoint (host name checked) with the signed URL and copies the bytes both ways.
- **Client ID** `<Cognito identity ID>-<install suffix>`, e.g. `eu-north-1:1b2c3d4e-...-testA`: also the
  `applicationId` of the status request and reply topic. Two installs of one account have different IDs, so both stay
  connected. HiveMQ fixes the client ID when it is built, so Android builds a client per identity; a sign-in with
  another account connects with that account's identity ID.
- **No AWS IoT access.** An identity the admin has not approved signs in, gets credentials, and AWS IoT then refuses
  it: it closes the connection after the CONNECT and before any CONNACK (or answers the upgrade with HTTP 403). The
  client reports `MqttConnectionState.REFUSED` (iOS: the transport's `onConnectionRefused`; Android: the relay saw an
  answer from AWS, and no CONNACK came), `ConnectMqttSource` throws `IotAccessDeniedException`, and the device list
  shows "No devices yet", not the error screen. The client then makes no attempt by itself (CocoaMQTT's automatic
  reconnect is turned off, HiveMQ's reconnect is held); "Search again" and the return to the foreground make one new
  attempt each, with new credentials. A socket error before any answer (no network, DNS) is still `FAILED` and the
  error screen, and the clients keep reconnecting.
- **Logs.** No credential, token or signed URL is logged: the iOS transport logs host, port, path, `X-Amz-Date` and
  the query parameter names; the credentials cache logs the expiry and a short hash of the access key ID. On the
  Simulator, CFNetwork logs the full URL of a failing WebSocket request (a device shows it as `<private>`); never copy
  such a log line unredacted.

### iOS client
iOS uses the same topics and the same shared code from `ConnectUseCase` up; only the `IotManager` differs. The MQTT
socket is CocoaMQTT (Swift, MQTT 3.1.1, clean session, automatic reconnect), wrapped as `CocoaMqttTransport` in
`iosApp` and passed to `initShared` as the Kotlin `MqttTransport`: plain TCP to the LAN broker (`init()`), or MQTT
over WebSocket to AWS IoT (`init(webSocketUrlProvider:)`). The transport only moves packets and reports
`onConnecting` / `onConnected` (CONNACK accepted) / `onDisconnected` / `onConnectionRefused` (the broker answered and
refused: a refusing CONNACK, an upgrade answered with an HTTP error, or a WebSocket that closed before the CONNACK) /
`onMessage` / `onPublishAcknowledged` (PUBACK of the packet identifier that `publish` returned); `disconnect()` closes
the connection and turns the automatic reconnect off until the next `connect`. `IosMqttClientManager`
(`core/data` iosMain) implements the `IotManager` contract on top:
- one connect attempt at a time: `connect()` on a connected client returns at once; while an attempt runs (a caller's
  or CocoaMQTT's automatic reconnect) it waits for that attempt, up to 10 s, and returns `FAILED` if it doesn't
  connect in time or the attempt ends in a disconnect;
- CocoaMQTT doesn't restore subscriptions on a clean session, so after every CONNACK the client sends SUBSCRIBE again
  for every open `subscribe` flow; the broker then replays the retained snapshot, as on Android;
- the transport reports each PUBLISH once, so the client matches the topic against the open filters (`+`, `#`) and
  decodes the payload as UTF-8;
- cancelling a collector sends one UNSUBSCRIBE for its filter (none while another open flow uses the same filter);
- `publishString` returns on the broker's PUBACK, as HiveMQ's QoS 1 publish does on Android, and throws when not
  connected, when the transport refuses the message, when the connection drops first or after 10 s without a PUBACK
  (details in `watering.md`).

`NetworkConnectivity` on iOS is `IosNetworkConnectivity` over `NWPathMonitor`; `initShared` logs each change
("Pliuskis network: online" / "offline") to the unified log.

Broker settings on iOS: the `MqttConnection` key of Info.plist (`MQTT_CONNECTION` of `Base.xcconfig`) picks the
connection. Release (`awsIot`) connects to AWS IoT over Cognito, as above: the endpoint `AMAZON_IOT_ENDPOINT` on port
443. Debug (`lan`) uses plaintext MQTT on port 1883 to the LAN broker (no TLS, no certificate in the app), unless it
is launched with `-awsIotCheck YES` (`AwsIotCheck`, the dev stack's AWS IoT). The LAN host is
`BROKER_HOST` of the active xcconfig, set per configuration in the gitignored `iosApp/Configuration/Secrets.xcconfig`
(`BROKER_HOST[config=Debug]`; template `Secrets.template.xcconfig`): `localhost` on the Simulator, the Mac's LAN IP
(`ipconfig getifaddr en0`, the same value as `DEBUG_BROKER_HOST`) for a real iPhone. The build writes it into the
`BrokerHost` key of Info.plist, and `BrokerConfiguration.settings(infoDictionary:clientId:)` (`iosApp`) reads it
there; no source holds a host or an endpoint. A missing
host or endpoint is empty, so the connect fails and the device list shows its error screen. Info.plist also has
`NSLocalNetworkUsageDescription` (no `NSBonjourServices`; there is no Bonjour). When the user denies Local Network
access, the socket never reaches the broker: the transport reports a disconnect, or nothing at all and the 10 s wait
ends, so `connect()` returns `FAILED` and the device list shows the error screen with retry; retry after allowing the
permission in Settings connects. The prompt never shows on the Simulator.

### iOS device list
The SwiftUI device list (`iosApp`) shows the same shared `DevicesViewModel` state; Swift only draws it. `RootView`
puts `LiveDeviceList` inside `DevicesNavigation`, a `NavigationSplitView` that is a stack on iPhone and list beside
detail on iPad (the counterpart of Android's list-detail). `LiveDeviceList` owns `DevicesViewModel` and
`NetworkStatusViewModel` through `IosViewModelStoreOwner`, collects both while it is shown, and passes the current
values to the stateless `DeviceListScreen(state:isOffline:selection:onRetry:)`:
- `Loading`: a progress indicator; `Success`: "Your garden", an inset-grouped `List` of rows in the order Kotlin sends
  them (sorted by name in `ObserveDevicesUseCase`; Swift never sorts), each with its type symbol in a tinted circle,
  its name and Online/Offline; `Success` with no devices (also the 3 s timeout): "No devices yet" with a "Search
  again" button; `Error`: "Couldn't load your devices" with a Retry button. Both buttons call
  `DevicesViewModel.retry()`. No timer or retry logic in Swift.
- The offline banner comes from `NetworkStatusViewModel.isOffline` (`shared` iosMain, `createNetworkStatusViewModel()`),
  the iOS counterpart of Android's app-level `PliuskisAppState.isOffline`: `NetworkConnectivity.isOnline` negated,
  `false` until the monitor reports. The banner, a Liquid Glass capsule on iOS 26, sits above the list and doesn't
  replace it.
- The bottom toolbar shows a one-line status from the Kotlin state ("5 devices · 3 online", "Looking for devices…",
  "Not connected"), marked `story1.kotlinValue` for STORY-1's launch UI test.
- A tapped row selects `ControlRoute(deviceId:deviceName:)`; `DevicesNavigation` shows the control screen it is given
  for that route (`LiveControlScreen` in the app), one screen per device (`.id(deviceId)`). The list's ViewModels
  stay alive while a control screen shows.
- Accessibility identifiers: `deviceList.loading`, `deviceList.row.<deviceId>` (label the name, value
  Online/Offline), `deviceList.empty`, `deviceList.searchAgain`, `deviceList.error`, `deviceList.retry`,
  `deviceList.offlineBanner`, `control.screen`, `control.title`. UI tests drive the screen with fixed states through `-uiTestScenario` (Debug
  only, see `docs/testing.md`).

### iOS control screen
`LiveControlScreen` (`iosApp`) owns the shared `ControlViewModel` through `IosViewModelStoreOwner`
(`createControlViewModel(deviceId)`), collects `controlUiState` and `wateringState` while it is shown and passes them
to the stateless `ControlScreen(deviceName:state:wateringState:onWater:onRetry:)` (the status and water flows on both
apps: `device-status.md`, `watering.md`). Opening it connects if needed, publishes one status request and subscribes
to `application/{clientId}/{deviceId}/status`, all in Kotlin, as on Android. Back pops the screen; SwiftUI releases the
owner, which clears the ViewModel once, and the status subscription sends its UNSUBSCRIBE.
- `Loading`: a progress indicator. `Success`: the health card ("Feeling great" or "Thirsty", with "Updated …"),
  "Conditions" with the soil moisture and water tank readings as percentages, and the "Water now" button pinned below.
  `Error`: "Can't reach your plant" (the message is not shown) with a Retry button that calls
  `ControlViewModel.retry()`.
- The button exists only in `Success` and calls `ControlViewModel.waterPlant()` once per tap. Its label follows
  `ControlViewModel.wateringState`, as on Android: "Watering…" (SENDING), "Watering started" with a success haptic
  (STARTED, back to IDLE after 3 s), "Try again" with an error line (FAILED). Kotlin ignores a tap while SENDING.
- `ControlStatusText` maps `HumidityStatus` and `WaterLevelStatus` to the shared wording and formats the reading time
  with `Date.FormatStyle` for the environment's `\.locale` and `\.timeZone`: only the time for a reading from today,
  day, month and time otherwise. The thresholds and the percentages come from Kotlin (`DeviceStatusMapper`); Swift
  only switches over the enum values.
- `.refreshOnForeground { viewModel.refresh() }` asks for the status again after a return from the background; the
  Kotlin ViewModel also asks after every automatic reconnect (see "Reconnects and foreground refresh").
- Accessibility identifiers: `control.screen`, `control.title` (the navigation bar title, label = device name, every
  state), `control.loading`, `control.health`, `control.humidity`, `control.waterLevel`, `control.updatedAt`,
  `control.water`, `control.wateringFailed`, `control.error`, `control.retry`.

## Key files
- Screen: `feature/devices/.../DeviceListScreen.kt`, `DevicesListDetail.kt` (list-detail layout);
  `feature/control/.../ControlScreen.kt` (`ControlPane`)
- ViewModel and UI state (shared): `core/presentation/.../devices/DevicesViewModel.kt`, `DevicesListScreenState.kt`,
  `DeviceListItem.kt`
- Error and empty UI: `core/designsystem/.../component/ErrorScreen.kt`, `InfoScreen.kt` (see `docs/design-system.md`)
- Use case: `core/domain/.../devicelist/ObserveDevicesUseCase.kt` (+ `DeviceEvent`, `Device`, `DeviceDomainModel`)
- Reconnects: `core/domain/.../connect/Reconnectable.kt`, `ObserveReconnectsUseCase.kt`;
  `core/data/.../mqttclient/ReconnectListener.kt` (Android); `ControlViewModel` (status request per reconnect,
  `refresh()`); `DevicesViewModel.refresh()`; `iosApp/iosApp/ForegroundRefresh.swift` (`refreshOnForeground`)
- Connect: `core/domain/.../connect/ConnectUseCase.kt`, `IotAccessDeniedException.kt`, `core/data/.../ConnectMqttSource.kt`
  (a failed connect throws; a refused identity throws `IotAccessDeniedException`)
- Release connection (AWS IoT over Cognito): `core/data/src/commonMain/.../aws/SigV4WebSocketUrl.kt`
  (`signAwsIotWebSocketUrl`), `AwsIotCredentialsCache.kt`, `CognitoIdentityClient.kt`, `AwsIotSettings.kt`;
  Android `core/data/src/androidMain/.../mqttclient/AwsIotMqttClientManager.kt`, `SignedWebSocketRelay.kt`
  (`AwsIotWebSocketTarget`), `.../di/AndroidDataModule.kt`; iOS `iosApp/iosApp/AwsIotWebSocketUrlProvider.swift`,
  `MqttWebSocketUrlProvider.swift`, `AwsIotCheck.swift`, `shared/src/iosMain/.../AwsIotConfiguration.kt`,
  `SharedEntryPoint.kt` (`initSharedOverAwsIot`, `signedAwsIotWebSocketUrl`)
- Data: `core/data/.../devices/SubscribeDeviceTopics.kt`, `core/data/.../mqttclient/HiveMqttClientManager.kt`, `MqttClientProducer.kt` (`createMqttClient`)
- iOS client: `iosApp/iosApp/CocoaMqttTransport.swift`; `core/data/src/iosMain/.../mqttclient/MqttTransport.kt`,
  `IosMqttClientManager.kt`, `TopicFilter.kt`; `.../util/IosNetworkConnectivity.kt`, `NwPathMonitorPathSource.kt`;
  `.../di/IosDataModule.kt`; `shared/src/iosMain/.../SharedEntryPoint.kt` (`initShared`), `NetworkStatusLog.kt`
- Broker settings: `app/.../broker/BrokerModule.kt` (debug or release `MqttSettings` and the release `AwsIotSettings`
  from `BuildConfig`); `core/data/.../mqttclient/MqttSettings.kt` (`connectionTarget`), `MqttTlsSetup.kt`
- Client ID: `core/data/.../clientid/ClientIdProvider.kt` (+ `ClientIdStore`, `SharedPreferencesClientIdStore`; iOS:
  `core/data/src/iosMain/.../clientid/UserDefaultsClientIdStore.kt`, `shared/src/iosMain/.../InstallClientId.kt`)
- iOS device list: `iosApp/iosApp/DeviceListScreen.swift`, `LiveDeviceList.swift`, `DevicesNavigation.swift`
  (`ControlRoute`), `RootView.swift`, `Flora.swift`, `UITestScenario.swift` (Debug only);
  `shared/src/iosMain/.../NetworkStatusViewModel.kt` (offline banner state)
- iOS control screen: `iosApp/iosApp/LiveControlScreen.swift`, `ControlScreen.swift`, `ControlStatusText.swift`;
  `shared/src/iosMain/.../SharedEntryPoint.kt` (`createControlViewModel`)
- iOS broker settings: `iosApp/iosApp/BrokerConfiguration.swift`, `PliuskisApp.swift`; `iosApp/project.yml` (Info.plist
  keys); `iosApp/Configuration/Base.xcconfig` (`MQTT_CONNECTION`), `Secrets.template.xcconfig` (`BROKER_HOST`, the
  `COGNITO_*`, `AWS_REGION` and `AMAZON_IOT_ENDPOINT` values)
- Firmware (`rsetkus/pico-pliuskis`): `main.py` (`mqtt_connect`, `connect_with_backoff`, ping in the main loop) and
  its `docs/features/discovery.md`

## Gotchas & decisions
- **`IotManager` is the contract for every platform client.** Its KDoc states it: `connect()` and `publishString()`
  suspend and never block the caller; `connect()` is safe when already connected or connecting; subscriptions survive
  a reconnect; `reconnects()` emits once per CONNACK after a lost connection. On Android, status requests and the water
  command publish through the HiveMQ async API.
- **Each screen that uses MQTT connects for itself.** Nothing connects at app launch, and the splash screen doesn't wait
  for MQTT. `DevicesViewModel` connects before discovery, and `ControlViewModel` connects before it requests or
  subscribes to status. The control screen can't rely on the device list: after process death the Android pane
  navigator restores the selected device, and its `ControlViewModel` can start before discovery has connected.
- **HiveMQ throws on a second connect.** `connect()` on a client that is already connected or connecting throws
  `MqttClientStateException`. `HiveMqttClientManager.connect` checks the client state first: connected returns at once,
  and connecting or reconnecting waits for that attempt. A concurrent connect that wins the race (the exception) is
  waited on the same way. So re-collecting the device list (back from background after the 5 s stop timeout) doesn't
  error, and a slow reconnect shows `Loading` and then the result instead of an early "No devices yet".
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
- **AWS IoT Core** (the Release builds, through Cognito; see [sign-in.md](sign-in.md) and
  [The release connection](#the-release-connection-aws-iot-over-cognito)). The private repository
  `rsetkus/pliuskis-backend` sets up the permissions with Terraform (its README and
  `modules/cognito-iot/iot_permissions.tf`). A signed-in Cognito identity gets AWS IoT access only after the admin
  attaches the app's IoT policy to it, and AWS IoT allows an action only when both that policy and the identity pool
  role's IAM policy allow it. The app may then connect only with a client ID that starts with its own identity ID
  (`{identityId}-{install suffix}`). It may subscribe to `devices/+/info`, `devices/+/availability` and its own reply
  topic `application/{clientId}/+/status`, and publish to `devices/{id}/status` and `devices/{id}/command`. Neither
  policy has `iot:RetainPublish`, so the app's publishes are never retained. AWS IoT refuses any other client ID, topic
  filter or topic, and another client's reply topic.
  Retained discovery works on Mosquitto (the Debug builds) but not on AWS IoT. AWS IoT Core sends a retained message
  on subscribe only to a filter that matches its topic exactly, never to a wildcard filter such as `devices/+/info`. So
  on AWS IoT the app sees a device only after the device publishes again while the app is subscribed, for example
  when the device reconnects. Epic PLIUS-44 replaces this discovery with per-user topics and a devices API.
- Every device is visible to every app; there is no per-user ownership yet.
