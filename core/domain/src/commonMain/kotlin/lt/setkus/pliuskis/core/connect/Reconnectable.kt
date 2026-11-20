package lt.setkus.pliuskis.core.connect

import kotlinx.coroutines.flow.Flow

/** The MQTT client's reconnects, as the domain sees them. */
interface Reconnectable {
    /**
     * Emits once each time the client is connected again after a lost connection (a new CONNACK), and not for the
     * client's first connection. It replays nothing: a collector gets only the reconnects after it started.
     */
    fun reconnects(): Flow<Unit>
}
