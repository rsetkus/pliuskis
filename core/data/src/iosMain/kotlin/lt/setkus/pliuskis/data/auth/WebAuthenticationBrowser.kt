package lt.setkus.pliuskis.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import lt.setkus.pliuskis.core.auth.SignInCancelledException
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorCodeCanceledLogin
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS [AuthorizationBrowser]: Cognito's managed login in an `ASWebAuthenticationSession` over the app's key window. It
 * shares cookies with Safari (no ephemeral session), so a returning user who is still signed in with Google or Apple
 * doesn't type the password again. The redirect URI's scheme is the session's callback scheme. Closing the sheet is
 * [SignInCancelledException].
 */
internal class WebAuthenticationBrowser : AuthorizationBrowser {

    private val anchor = KeyWindowAnchor()

    /** The running session. `ASWebAuthenticationSession` must be kept alive while it is shown. */
    private var running: ASWebAuthenticationSession? = null

    override suspend fun authorize(url: String, redirectUri: String): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val session = newSession(url, redirectUri.substringBefore(':')) { redirect, error ->
                running = null
                when {
                    redirect != null -> continuation.resume(redirect)
                    error?.code == ASWebAuthenticationSessionErrorCodeCanceledLogin ->
                        continuation.resumeWithException(SignInCancelledException())
                    else -> continuation.resumeWithException(
                        IllegalStateException("The web authentication session failed (code ${error?.code})")
                    )
                }
            }
            session.presentationContextProvider = anchor
            session.prefersEphemeralWebBrowserSession = false
            running = session
            continuation.invokeOnCancellation { dispatch_async(dispatch_get_main_queue()) { session.cancel() } }
            if (!session.start()) {
                running = null
                continuation.resumeWithException(IllegalStateException("The web authentication session didn't start"))
            }
        }
    }

    // The URL-scheme initialiser: the Callback one needs iOS 17.4, the app supports 17.0.
    @Suppress("DEPRECATION")
    private fun newSession(
        url: String,
        callbackScheme: String,
        completion: (redirect: String?, error: NSError?) -> Unit,
    ): ASWebAuthenticationSession = ASWebAuthenticationSession(
        uRL = checkNotNull(NSURL.URLWithString(url)) { "The managed login URL is not valid" },
        callbackURLScheme = callbackScheme,
        completionHandler = { redirect: NSURL?, error: NSError? -> completion(redirect?.absoluteString, error) },
    )
}

/** Shows the session over the app's key window. */
private class KeyWindowAnchor : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession,
    ): ASPresentationAnchor =
        UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .firstNotNullOfOrNull { scene -> scene.keyWindow }
            ?: UIWindow()
}
