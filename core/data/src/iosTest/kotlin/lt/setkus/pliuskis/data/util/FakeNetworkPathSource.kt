package lt.setkus.pliuskis.data.util

/**
 * Stands in for the NWPathMonitor path source: a test reports a satisfied or unsatisfied path, as NWPathMonitor's
 * update handler would.
 */
class FakeNetworkPathSource : NetworkPathSource {
    private var listener: NetworkPathListener? = null

    override fun start(listener: NetworkPathListener) {
        this.listener = listener
    }

    override fun stop() {
        listener = null
    }

    fun report(satisfied: Boolean) =
        checkNotNull(listener) { "Nobody started the path source; is isOnline collected?" }.onPathChanged(satisfied)
}
