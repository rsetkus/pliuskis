package lt.setkus.pliuskis.signin

import lt.setkus.pliuskis.BuildConfig
import lt.setkus.pliuskis.data.auth.SignInSettings
import org.koin.dsl.module

/**
 * The Cognito managed login the app signs in with: the dev stack in the debug build (`DEBUG_COGNITO_*`), the prod
 * stack in the release build (`COGNITO_*`). The values come from BuildConfig, which the secrets plugin fills from
 * secrets.properties (or default.secrets.properties, which holds only dummy values).
 */
val signInSettingsModule = module {
    single {
        if (BuildConfig.DEBUG) {
            SignInSettings(
                userPoolId = BuildConfig.DEBUG_COGNITO_USER_POOL_ID,
                appClientId = BuildConfig.DEBUG_COGNITO_APP_CLIENT_ID,
                loginDomain = BuildConfig.DEBUG_COGNITO_LOGIN_DOMAIN,
                redirectUri = BuildConfig.DEBUG_COGNITO_REDIRECT_URI,
            )
        } else {
            SignInSettings(
                userPoolId = BuildConfig.COGNITO_USER_POOL_ID,
                appClientId = BuildConfig.COGNITO_APP_CLIENT_ID,
                loginDomain = BuildConfig.COGNITO_LOGIN_DOMAIN,
                redirectUri = BuildConfig.COGNITO_REDIRECT_URI,
            )
        }
    }
}
