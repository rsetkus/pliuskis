package lt.setkus.pliuskis.data.auth

/**
 * The Cognito user pool's managed login of one stack: the dev stack for Debug builds, the prod stack for Release
 * builds. Each app reads the values from its gitignored configuration (`secrets.properties` on Android,
 * `Secrets.xcconfig` on iOS); none is in the repository.
 *
 * @property userPoolId the user pool ID, `<region>_<id>`; its region is the region of the Cognito API ([region])
 * @property appClientId the app client ID (a public client: no client secret)
 * @property loginDomain the managed login domain, a host name without `https://`
 * @property redirectUri where the managed login sends the browser back to the app, e.g. `lt.setkus.pliuskis://signin`
 */
class SignInSettings(
    val userPoolId: String,
    val appClientId: String,
    val loginDomain: String,
    val redirectUri: String,
) {
    /** The AWS region of the user pool, from its ID. */
    val region: String
        get() = userPoolId.substringBefore('_')

    /** False when a value is missing, for example in a build without a configured stack. */
    val isComplete: Boolean
        get() = listOf(userPoolId, appClientId, loginDomain, redirectUri).none(String::isBlank) &&
            userPoolId.contains('_') && redirectUri.contains(':')
}
