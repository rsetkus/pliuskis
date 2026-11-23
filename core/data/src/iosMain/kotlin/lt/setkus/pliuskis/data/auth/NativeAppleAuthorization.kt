package lt.setkus.pliuskis.data.auth

import kotlinx.cinterop.BetaInteropApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import lt.setkus.pliuskis.core.auth.AppleAuthorization
import lt.setkus.pliuskis.core.auth.SignInCancelledException
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationErrorCanceled
import platform.AuthenticationServices.ASPresentationAnchor
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS [AppleAuthorization]: the native Sign in with Apple sheet (`ASAuthorizationController` with an Apple ID request,
 * no scopes), over the app's key window. Its code belongs to the app's App ID (bundle ID), so the revoke endpoint
 * exchanges it with that client ([AppleRevokeClient.APP_ID]). It needs the Sign in with Apple entitlement
 * (`iosApp/project.yml`). Closing the sheet is [SignInCancelledException].
 */
internal class NativeAppleAuthorization : AppleAuthorization {

    /** The running request. The controller holds its delegate weakly, so the delegate is kept here while it runs. */
    private var running: AppleIdRequestDelegate? = null

    override suspend fun authorizationCode(): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val request = ASAuthorizationAppleIDProvider().createRequest()
            val controller = ASAuthorizationController(authorizationRequests = listOf(request))
            val delegate = AppleIdRequestDelegate { result ->
                running = null
                result.fold(
                    onSuccess = { code -> continuation.resume(code) },
                    onFailure = { error -> continuation.resumeWithException(error) },
                )
            }
            controller.delegate = delegate
            controller.presentationContextProvider = delegate
            running = delegate
            continuation.invokeOnCancellation { dispatch_async(dispatch_get_main_queue()) { controller.cancel() } }
            controller.performRequests()
        }
    }
}

/** Answers the Apple ID request once: the authorization code, [SignInCancelledException], or a failure. */
private class AppleIdRequestDelegate(
    private val onResult: (Result<String>) -> Unit,
) : NSObject(),
    ASAuthorizationControllerDelegateProtocol,
    ASAuthorizationControllerPresentationContextProvidingProtocol {

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization: ASAuthorization,
    ) {
        val credential = didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
        val code = credential?.authorizationCode?.let(::utf8String)
        onResult(
            if (code != null) {
                Result.success(code)
            } else {
                Result.failure(IllegalStateException("Apple returned no authorization code"))
            }
        )
    }

    override fun authorizationController(controller: ASAuthorizationController, didCompleteWithError: NSError) {
        onResult(
            Result.failure(
                if (didCompleteWithError.code == ASAuthorizationErrorCanceled) {
                    SignInCancelledException()
                } else {
                    IllegalStateException("The Apple ID request failed (code ${didCompleteWithError.code})")
                }
            )
        )
    }

    override fun presentationAnchorForAuthorizationController(
        controller: ASAuthorizationController,
    ): ASPresentationAnchor =
        UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .firstNotNullOfOrNull { scene -> scene.keyWindow }
            ?: UIWindow()
}

@OptIn(BetaInteropApi::class)
private fun utf8String(data: NSData): String? = NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
