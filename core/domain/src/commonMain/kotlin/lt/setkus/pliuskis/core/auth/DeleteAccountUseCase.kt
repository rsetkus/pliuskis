package lt.setkus.pliuskis.core.auth

import arrow.core.Either
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import lt.setkus.pliuskis.core.BaseUseCase

/**
 * Deletes the signed-in user's account (App Store guideline 5.1.1(v), Google Play account deletion): Cognito
 * DeleteUser with the stored access token, then the stored session is removed. When DeleteUser fails, nothing is
 * removed and the user stays signed in.
 *
 * A user who signed in with Apple (the ID token's `identities` claim, [signInProvider]) first confirms with Apple
 * again: [appleAuthorization] returns a fresh Apple authorization code, and [appleTokenRevoker] has the backend revoke
 * the user's Apple tokens with it. DeleteUser runs only after the revoke succeeded. If the user cancels the Apple
 * confirmation ([SignInCancelledException]) or the revoke fails, that is the failure and nothing is deleted. Google
 * users, and a session whose provider can't be read, go straight to DeleteUser.
 */
class DeleteAccountUseCase(
    private val authenticator: Authenticator,
    private val tokenStore: TokenStore,
    private val appleAuthorization: AppleAuthorization,
    private val appleTokenRevoker: AppleTokenRevoker,
) : BaseUseCase<Unit, Unit> {

    override fun invoke(param: Unit): Flow<Either<Throwable, Unit>> = flow {
        emit(Either.catch { deleteAccount() })
    }

    private suspend fun deleteAccount() {
        val stored = checkNotNull(tokenStore.observe().first()) { "No signed-in user to delete" }
        if (stored.signInProvider() == AuthProvider.APPLE) {
            revokeAppleTokens(stored.accessToken)
        }
        authenticator.deleteUser(stored.accessToken)
        tokenStore.clear()
    }

    private suspend fun revokeAppleTokens(accessToken: String) {
        val authorizationCode = appleAuthorization.authorizationCode()
        appleTokenRevoker.revoke(authorizationCode, accessToken)
    }
}
