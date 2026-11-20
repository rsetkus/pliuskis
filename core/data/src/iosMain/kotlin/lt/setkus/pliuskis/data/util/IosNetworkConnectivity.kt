package lt.setkus.pliuskis.data.util

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.Foundation.NSRecursiveLock

/**
 * The iOS [NetworkConnectivity]: [isOnline] follows the path updates of [pathSource] (`NWPathMonitor` in the app):
 * true for a satisfied path, false otherwise, without repeats.
 *
 * Any number of collectors share one path source: the first collector starts it, the last one stops it, and a
 * collector that arrives while it runs gets the latest value at once.
 */
class IosNetworkConnectivity(private val pathSource: NetworkPathSource) : NetworkConnectivity {

    private val lock = NSRecursiveLock()
    private val collectors = mutableListOf<SendChannel<Boolean>>()
    private var latest: Boolean? = null

    private val pathListener = NetworkPathListener { satisfied ->
        lock.locked {
            latest = satisfied
            collectors.forEach { it.trySend(satisfied) }
        }
    }

    override val isOnline: Flow<Boolean> = callbackFlow {
        register(channel)
        awaitClose { unregister(channel) }
    }.buffer(Channel.UNLIMITED).distinctUntilChanged()

    private fun register(collector: SendChannel<Boolean>) = lock.locked {
        collectors += collector
        latest?.let { collector.trySend(it) }
        if (collectors.size == 1) pathSource.start(pathListener)
    }

    private fun unregister(collector: SendChannel<Boolean>) = lock.locked {
        collectors -= collector
        if (collectors.isEmpty()) {
            pathSource.stop()
            latest = null
        }
    }
}
