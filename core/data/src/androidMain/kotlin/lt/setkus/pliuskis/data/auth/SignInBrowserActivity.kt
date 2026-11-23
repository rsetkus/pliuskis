package lt.setkus.pliuskis.data.auth

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import co.touchlab.kermit.Logger

/**
 * Opens Cognito's managed login in a Custom Tab and learns how it ended (the AppAuth pattern). It opens the tab on its
 * first resume. When it resumes again, either [SignInRedirectActivity] brought it back with the redirect URL (the user
 * signed in) or the user closed the tab (cancelled); it answers [PendingAuthorization] and finishes. It has no UI of
 * its own (a translucent theme in the app manifest, which declares it with `launchMode="singleTask"`).
 */
class SignInBrowserActivity : Activity() {

    private var browserOpened = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        browserOpened = savedInstanceState?.getBoolean(KEY_BROWSER_OPENED) ?: false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (browserOpened) {
            PendingAuthorization.complete(intent.data?.toString())
            finish()
        } else {
            browserOpened = true
            openBrowser()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_BROWSER_OPENED, browserOpened)
    }

    private fun openBrowser() {
        val url = intent.getStringExtra(EXTRA_URL)
        if (url == null) {
            PendingAuthorization.complete(null)
            finish()
        } else {
            try {
                CustomTabsIntent.Builder().build().launchUrl(this, url.toUri())
            } catch (error: ActivityNotFoundException) {
                Logger.withTag(TAG).w(error) { "No browser can open the managed login" }
                PendingAuthorization.complete(null)
                finish()
            }
        }
    }

    internal companion object {
        private const val TAG = "SignIn"
        private const val EXTRA_URL = "lt.setkus.pliuskis.signin.URL"
        private const val KEY_BROWSER_OPENED = "browserOpened"

        /** Opens the managed login at [url]; from any [context], so it starts a task if it needs one. */
        fun intent(context: Context, url: String): Intent =
            Intent(context, SignInBrowserActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Brings the waiting activity back with the [redirect] from the managed login. */
        fun redirectIntent(context: Context, redirect: Intent): Intent =
            Intent(context, SignInBrowserActivity::class.java)
                .setData(redirect.data)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
