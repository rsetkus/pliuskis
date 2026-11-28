package lt.setkus.pliuskis.shared

import lt.setkus.pliuskis.data.auth.AppleRevokeSettings
import lt.setkus.pliuskis.data.auth.SignInSettings
import platform.Foundation.NSBundle

// The Info.plist keys of the sign-in settings; the build fills them from `Secrets.xcconfig` (see `project.yml`).
private const val USER_POOL_ID_KEY = "CognitoUserPoolId"
private const val APP_CLIENT_ID_KEY = "CognitoAppClientId"
private const val LOGIN_DOMAIN_KEY = "CognitoLoginDomain"
private const val REDIRECT_URI_KEY = "CognitoRedirectUri"
private const val APPLE_REVOKE_API_URL_KEY = "AppleRevokeApiUrl"

/**
 * The Cognito managed login of this build, from the app's Info.plist: the dev stack's values in the Debug build, the
 * prod stack's in Release (`COGNITO_*` of the active configuration in `Secrets.xcconfig`). A missing key is empty;
 * sign-in then fails with an error state instead of opening a browser.
 */
internal fun signInSettingsFromInfoPlist(bundle: NSBundle = NSBundle.mainBundle): SignInSettings = SignInSettings(
    userPoolId = bundle.infoString(USER_POOL_ID_KEY),
    appClientId = bundle.infoString(APP_CLIENT_ID_KEY),
    loginDomain = bundle.infoString(LOGIN_DOMAIN_KEY),
    redirectUri = bundle.infoString(REDIRECT_URI_KEY),
)

/**
 * The backend's Apple revoke API of this build's stack, from the app's Info.plist (`APPLE_REVOKE_API_URL` of the active
 * configuration in `Secrets.xcconfig`). iOS gets the Apple code from the native sheet with the app's App ID, so it
 * needs no Services ID. A missing key is empty; an Apple user's account deletion then fails and keeps the account.
 */
internal fun appleRevokeSettingsFromInfoPlist(bundle: NSBundle = NSBundle.mainBundle): AppleRevokeSettings =
    AppleRevokeSettings(apiUrl = bundle.infoString(APPLE_REVOKE_API_URL_KEY))

private fun NSBundle.infoString(key: String): String = objectForInfoDictionaryKey(key) as? String ?: ""
