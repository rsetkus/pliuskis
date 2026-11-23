package lt.setkus.pliuskis.data.auth

import android.app.Activity
import android.os.Bundle

/**
 * Receives the managed login's redirect to the app (the app manifest gives it an intent filter for the redirect URI's
 * scheme) and hands it to [SignInBrowserActivity], which closes the Custom Tab above it. It shows nothing.
 */
class SignInRedirectActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(SignInBrowserActivity.redirectIntent(this, intent))
        finish()
    }
}
