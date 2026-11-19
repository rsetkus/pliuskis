package lt.setkus.pliuskis.core.devicelist

import kotlinx.coroutines.flow.Flow

/** Fake [DeviceSubscribable] whose discovery subscription emits [events]. */
class FakeDeviceSubscribable(private val events: Flow<DeviceEvent>) : DeviceSubscribable {
    override fun subscribeDevices(): Flow<DeviceEvent> = events
}
