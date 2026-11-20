package lt.setkus.pliuskis.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import lt.setkus.pliuskis.data.util.NetworkConnectivity
import org.koin.dsl.module
import org.koin.dsl.onClose
import platform.Foundation.NSLog

/**
 * Writes every change of [connectivity] to the unified log ("Pliuskis network: online" / "offline") while the shared
 * graph runs, so the connectivity monitor can be followed in the running app with
 * `xcrun simctl spawn booted log stream --predicate 'process == "iosApp"'`.
 */
internal class NetworkStatusLog(connectivity: NetworkConnectivity) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Constant format strings only: a Kotlin String passed as a variadic `%@` argument of NSLog crashes.
        connectivity.isOnline
            .onEach { online -> NSLog(if (online) "Pliuskis network: online" else "Pliuskis network: offline") }
            .launchIn(scope)
    }

    override fun close() = scope.cancel()
}

/** Starts [NetworkStatusLog] with the graph and stops it when the graph stops. */
internal val networkStatusLogModule = module {
    single(createdAtStart = true) { NetworkStatusLog(get()) } onClose { it?.close() }
}
