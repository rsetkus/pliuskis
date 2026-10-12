# Device discovery
**Last verified**: 2026-10-12
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

1. `MainActivityViewModel` connects the shared HiveMQ client (`ConnectUseCase`). The client reconnects automatically and
   re-subscribes, after which the broker replays the retained snapshot again.
2. `DevicesViewModel` collects `ObserveDevicesUseCase`. The data layer (`SubscribeDeviceTopics`) subscribes to both
   wildcard filters, takes `deviceId` from the topic, and emits `DeviceEvent.Info` / `DeviceEvent.Availability`.
3. `ObserveDevicesUseCase` folds the events into a registry of info and online state per `deviceId`. It emits the full
   list, sorted by name, after every event. State lives only in this fold; nothing is cached on disk.
4. `DevicesViewModel` maps the list to `DevicesListScreenState.Success`. If nothing arrives within 3 s it emits an empty
   list, because MQTT has no "end of retained snapshot" marker. A device's card shows an online/offline indicator.
5. Tapping a device navigates to `control/{deviceId}`; status and commands use that same `deviceId`.

Error path: a malformed info payload is logged and skipped, and the other devices keep updating. Only a failure of the
subscription itself turns the screen into `Error`.

## Key files
- Screen: `feature/devices/.../DeviceListScreen.kt`, `DevicesViewModel.kt`, `DeviceListItem.kt`
- Use case: `core/domain/.../devicelist/ObserveDevicesUseCase.kt` (+ `DeviceEvent`, `Device`, `DeviceDomainModel`)
- Data: `core/data/.../devices/SubscribeDeviceTopics.kt`, `core/data/.../mqttclient/HiveMqttClientManager.kt`, `MqttClientProducer.kt`
- Firmware: `picow-pliuskis/main.py` (`mqtt_connect`, `connect_with_backoff`, ping in the main loop)

## Gotchas & decisions
- **The Last Will only fires on an ungraceful loss** (power cut, WiFi drop, missed keepalive). A clean `DISCONNECT`
  suppresses it, so the firmware publishes `offline` itself before disconnecting.
- **Keepalive needs pings.** `umqtt.simple` never sends PINGREQ on its own, so the firmware pings every
  `keepalive / 2` seconds. Without that, the broker drops an idle device after 1.5 × keepalive and marks it offline.
- **Offline is reported after up to 1.5 × keepalive** (90 s with the firmware's 60 s), not instantly, for losses that
  don't close the TCP connection.
- **The topic is the source of truth for `deviceId`.** The payload's `deviceId` is overwritten with the topic's, because
  command and status topics are built from it.
- **Removing a device** means publishing an empty retained payload to its `info` topic
  (`mosquitto_pub -r -n -t devices/{id}/info`). Clear `availability` the same way.
- **Unsubscribe on close.** `HiveMqttClientManager.subscribe` unsubscribes in `awaitClose`; otherwise every re-collect
  leaks a callback. Two collectors of the same topic filter at once would unsubscribe each other.
- **AWS IoT Core** supports retained messages and Last Will, but the device policy needs `iot:RetainPublish`, and the
  app policy needs `iot:Subscribe` on the `devices/+/…` topic filters plus `iot:Receive` on the matching topics.
- Every device is visible to every app; there is no per-user ownership yet.
