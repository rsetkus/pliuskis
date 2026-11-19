package lt.setkus.pliuskis.data.clientid

/**
 * STORY-5: fake [ClientIdStore] that keeps the client ID in memory. Two [ClientIdProvider] instances over the same
 * store behave like two processes of one app install over the same persisted value.
 */
class InMemoryClientIdStore(initial: String? = null) : ClientIdStore {
    var stored: String? = initial
        private set

    override fun read(): String? = stored

    override fun write(clientId: String) {
        stored = clientId
    }
}
