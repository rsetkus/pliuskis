package lt.setkus.pliuskis.data.util

import platform.Foundation.NSRecursiveLock
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_monitor_t
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

private const val PATH_QUEUE_LABEL = "lt.setkus.pliuskis.network-path"

/**
 * A [NetworkPathSource] backed by Apple's `NWPathMonitor` (Network framework). It reports the current path right after
 * [start], then every change, on its own serial queue (not the main queue, so a blocked main thread does not hold the
 * updates back). A cancelled monitor cannot start again, so every [start] creates a new one.
 */
class NwPathMonitorPathSource : NetworkPathSource {
    private val lock = NSRecursiveLock()
    private val queue = dispatch_queue_create(PATH_QUEUE_LABEL, null)
    private var monitor: nw_path_monitor_t = null

    override fun start(listener: NetworkPathListener) = lock.locked {
        monitor?.let { nw_path_monitor_cancel(it) }
        val newMonitor = nw_path_monitor_create()
        nw_path_monitor_set_queue(newMonitor, queue)
        nw_path_monitor_set_update_handler(newMonitor) { path ->
            listener.onPathChanged(nw_path_get_status(path) == nw_path_status_satisfied)
        }
        nw_path_monitor_start(newMonitor)
        monitor = newMonitor
    }

    override fun stop() = lock.locked {
        monitor?.let { nw_path_monitor_cancel(it) }
        monitor = null
    }
}
