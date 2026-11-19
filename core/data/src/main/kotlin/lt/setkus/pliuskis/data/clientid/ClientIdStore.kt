package lt.setkus.pliuskis.data.clientid

/** Keeps this app install's MQTT client ID across app processes. */
interface ClientIdStore {
    /** The stored client ID, or null if none was stored yet. */
    fun read(): String?

    fun write(clientId: String)
}
