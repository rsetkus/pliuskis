# Watering
**Last verified**: 2026-11-21 (iOS water result follows the PUBACK)
**Scope**: the "Water now" button of the control screen (Android and iOS), its watering feedback, and the `water` command it sends to the device.

## Summary
On the control screen of a device, the user taps "Water now" and the app publishes one `water` command to that
device. The device runs its pump for a fixed time and then sends a fresh status, which the open control screen shows
like any other reading (see `device-status.md`).

The button shows how the send went, from the shared `ControlViewModel.wateringState` (`WateringState`): "Watering…"
while the command is being sent, "Watering started" for 3 s once it is sent, and "Try again" with an error line when it
failed. It is feedback about the publish only: the device never confirms, and only the next status reply shows that
the soil got wetter. Both apps send the command and keep the state in the same shared code (`ControlViewModel` and
below); each platform only draws the state.

### How it works
| Topic | Retained | QoS | Published by | Payload |
|---|---|---|---|---|
| `devices/{deviceId}/command` | no | 1 | app, once per accepted tap | `{"command":"water"}` |

1. The button exists only in `ControlUiState.Success`, so the user can water only after the device has answered a
   status request. It is pinned to the bottom of the screen. Android: `WateringAction` in `ControlScreen.kt`
   (`feature/control`), wired by `ControlPane` as `onWaterButtonClick = viewModel::waterPlant`. iOS: `WaterAction` in
   `ControlScreen.swift` (`iosApp`), `onWater: { viewModel.waterPlant() }` from `LiveControlScreen`; accessibility
   identifier `control.water`.
2. `ControlViewModel.waterPlant()` sets `wateringState` to `SENDING` and runs `ExecuteWaterCommandUseCase(deviceId)` in
   `viewModelScope`. A tap while `SENDING` is ignored, in the ViewModel and in both buttons, so one tap sends one
   command.
3. `ExecuteWaterCommandUseCase` calls the port `CommandExecutable<String>`, which `dataModule` binds to
   `MqttExecuteWaterCommand`. It encodes `CommandMessage(Command.WATER_COMMAND)` as `{"command":"water"}` and
   publishes it to `devices/{deviceId}/command` through `IotManager.publishString` (QoS 1, not retained). On Android
   `HiveMqttClientManager` waits for HiveMQ's async QoS 1 publish to complete; on iOS `IosMqttClientManager` refuses
   it when not connected, hands it to `CocoaMqttTransport.publish` and waits up to 10 s for the broker's PUBACK of
   that packet identifier, failing if the transport refuses it, the connection drops or no PUBACK comes.
4. The publish returns `Either.Right`: `wateringState` becomes `STARTED` ("Watering started" with a check icon; on iOS
   also a success haptic), and after 3 s (`WATERING_CONFIRMATION_MILLIS`) goes back to `IDLE` ("Water now") unless
   another tap changed it. It returns `Either.Left`: `FAILED`, the button reads "Try again" and the line "Couldn't
   reach your plant. Try again." shows above it (iOS: identifier `control.wateringFailed`, and an error haptic).
   `FAILED` stays until the next tap, which sends again.
5. The device takes the command, closes the pump relay for `WATERING_SECONDS`, reads the soil again and replies to
   every app that asked for status recently, on `application/{applicationId}/{deviceId}/status`.
6. The control screen's status subscription receives the reply and shows the new moisture.

State lives only in the ViewModel's `wateringState` (a `StateFlow`), next to `controlUiState`; nothing is stored.
Error path: a failed publish is not retried by the app; the user taps "Try again". The device ignores a message that
isn't a known command.

## Key files
- Button: `feature/control/.../ControlScreen.kt` (`WateringAction`, `WaterButtonContent`, `ControlPane`),
  `res/values/strings.xml`; `iosApp/iosApp/ControlScreen.swift` (`WaterAction`), `LiveControlScreen.swift`
- ViewModel and state (shared): `core/presentation/.../control/ControlViewModel.kt` (`waterPlant`, `wateringState`),
  `WateringState.kt`
- Use case and port: `core/domain/.../command/water/ExecuteWaterCommandUseCase.kt`, `ExecuteWaterCommand.kt`,
  `core/domain/.../command/CommandExecutable.kt`
- Data: `core/data/.../command/water/MqttExecuteWaterCommand.kt` (topic), `core/data/.../command/Command.kt`
  (`Command`, `CommandMessage`: the payload); `core/data/.../di/KoinModules.kt` (`dataModule`)
- Tests: `core/presentation/src/commonTest/.../control/ControlViewModelWateringTest.kt` (the states, the ignored tap
  while sending); `shared/src/iosTest/.../Story6WaterNotRepeatedTest.kt` (a reconnect or a refresh never sends a water
  command); `shared/src/iosTest/.../WaterResultOnIosTest.kt` (iOS: `STARTED` when the broker acknowledges, `FAILED`
  after the connection dropped); `core/data/src/iosTest/.../IosMqttClientManagerPublishAckTest.kt` (the PUBACK wait
  and its failures); `feature/control/src/test/.../ControlScreenSnapshotTest.kt` (watering started, failed, sending);
  `iosApp/iosAppUITests/Story5ControlScreenUITests.swift` with the UI test scenarios `controlWatering` and
  `controlWateringFailed` and the tap counter `uiTestScenario.waterCount` (`docs/testing.md`)
- Firmware (`rsetkus/pico-pliuskis`): `lib/plant.py` (`Plant.on_message`, `take_water_request`, `Relay.pulse`),
  `main.py` (`WATERING_SECONDS`, `RELAY_ON`) and its `docs/features/status-and-watering.md`

## Gotchas & decisions
- **Only a tap waters.** Opening the screen, a reconnect, Retry and a foreground refresh publish status requests,
  never a water command; `Story6WaterNotRepeatedTest` guards it. The command is not retained, so the broker never
  replays it to a device that reconnects later.
- **"Watering started" means the broker took the command (PUBACK), not that the device watered.** On Android
  HiveMQ's QoS 1 publish completes on the PUBACK. On iOS the PUBACK wait is in `IosMqttClientManager`, because
  CocoaMQTT queues a QoS 1 message even while disconnected and returns a packet identifier for it (and drops the queue
  on a clean-session reconnect), so its return value alone proves nothing.
- **A tap while sending is dropped; a tap after it is not.** Taps in `STARTED` or `FAILED` send again, and the firmware
  waters once for each `water` command it takes (one message per pass of its main loop), so repeated taps water
  repeatedly.
- **The topic and the payload are a contract with the firmware.** `{"command":"water"}` is the only command; change
  `Command`, the topic and the firmware together.
- **No confirmation from the device.** The only feedback from the plant is the next status reply. If the status
  replies have stopped (see `device-status.md`), a tap that watered still shows the old reading.
- **`deviceId` comes from the selected device, not from a payload**, the same `deviceId` the status topics use (see
  `device-discovery.md`).
