package lt.setkus.pliuskis.core.systemstate

/** Fake [SystemStateRequestable] that records the device id of every request. */
class RecordingSystemStateRequestable : SystemStateRequestable {
    val requestedDeviceIds = mutableListOf<String>()

    override suspend fun requestSystemState(deviceId: String) {
        requestedDeviceIds += deviceId
    }
}
