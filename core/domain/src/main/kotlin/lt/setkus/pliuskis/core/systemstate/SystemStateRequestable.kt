package lt.setkus.pliuskis.core.systemstate

interface SystemStateRequestable {
    suspend fun requestSystemState(deviceId: String)
}
