package lt.setkus.pliuskis.data.util

/** Reports whether the device has a usable network path. On iOS it is [NwPathMonitorPathSource]. */
interface NetworkPathSource {
    /** Starts reporting path changes to [listener]. A start after [stop] begins again. */
    fun start(listener: NetworkPathListener)

    /** Stops reporting and releases the monitor. */
    fun stop()
}

/** Receives the path updates of a [NetworkPathSource]. */
fun interface NetworkPathListener {
    /** [satisfied] is true when the path can reach the network (NWPath status `satisfied`). */
    fun onPathChanged(satisfied: Boolean)
}
