package lt.setkus.pliuskis.data.auth

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicReference

/**
 * The one sign-in that waits for the browser, between [CustomTabsAuthorizationBrowser] (which waits) and
 * [SignInBrowserActivity] (which learns how the browser ended). Null means the browser closed without a redirect.
 */
internal object PendingAuthorization {
    private val current = AtomicReference<CompletableDeferred<String?>?>(null)

    /** Starts waiting for a redirect. A sign-in still waiting is answered as cancelled. */
    fun begin(): CompletableDeferred<String?> {
        val next = CompletableDeferred<String?>()
        current.getAndSet(next)?.complete(null)
        return next
    }

    /** Answers the waiting sign-in with the [redirect] URL, or null when the browser closed without one. */
    fun complete(redirect: String?) {
        current.getAndSet(null)?.complete(redirect)
    }

    /** Stops waiting for [pending] if it is still the current one (its sign-in was cancelled). */
    fun abandon(pending: CompletableDeferred<String?>) {
        current.compareAndSet(pending, null)
    }
}
