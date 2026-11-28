package lt.setkus.pliuskis.data.auth

/**
 * The Sign in with Apple client an authorization code belongs to. Apple only exchanges a code with the client it was
 * issued to, so the app tells the revoke endpoint which one: the iOS app's native sheet gives codes of the App ID
 * (bundle ID), Android's browser flow codes of the stack's Services ID.
 */
internal enum class AppleRevokeClient(val wireName: String) {
    /** The iOS app's App ID (the native Sign in with Apple sheet). */
    APP_ID("app_id"),

    /** The stack's Services ID (Android's browser flow). */
    SERVICES_ID("services_id"),
}
