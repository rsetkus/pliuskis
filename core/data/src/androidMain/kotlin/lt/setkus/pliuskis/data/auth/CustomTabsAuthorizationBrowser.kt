package lt.setkus.pliuskis.data.auth

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lt.setkus.pliuskis.core.auth.SignInCancelledException

/**
 * Android [AuthorizationBrowser]: Cognito's managed login in a Custom Tab, opened by [SignInBrowserActivity]. The
 * managed login redirects to the app's redirect URI, which [SignInRedirectActivity] receives (its intent filter has
 * the redirect URI's scheme). Closing the tab without signing in is [SignInCancelledException].
 */
internal class CustomTabsAuthorizationBrowser(private val context: Context) : AuthorizationBrowser {

    override suspend fun authorize(url: String, redirectUri: String): String {
        val pending = PendingAuthorization.begin()
        val redirect = try {
            withContext(Dispatchers.Main) { context.startActivity(SignInBrowserActivity.intent(context, url)) }
            pending.await()
        } finally {
            PendingAuthorization.abandon(pending)
        }
        return redirect ?: throw SignInCancelledException()
    }
}
