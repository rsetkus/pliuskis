package lt.setkus.pliuskis.core.connect

interface Connectable {
    suspend fun connect(): String
}
