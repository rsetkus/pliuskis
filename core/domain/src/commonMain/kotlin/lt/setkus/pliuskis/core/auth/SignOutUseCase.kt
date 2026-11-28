package lt.setkus.pliuskis.core.auth

import arrow.core.Either
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import lt.setkus.pliuskis.core.BaseUseCase

/**
 * Signs the user out: revokes the stored refresh token with Cognito once, then removes the stored session. The session
 * is removed even when the revoke fails (no network, say), so sign-out always works on the phone.
 */
class SignOutUseCase(
    private val authenticator: Authenticator,
    private val tokenStore: TokenStore,
) : BaseUseCase<Unit, Unit> {

    override fun invoke(param: Unit): Flow<Either<Throwable, Unit>> = flow {
        emit(Either.catch { signOut() })
    }

    private suspend fun signOut() {
        val stored = tokenStore.observe().first()
        if (stored != null) {
            // The error is not logged: it could carry request details. Only the fact is.
            Either.catch { authenticator.revoke(stored.refreshToken) }
                .onLeft { Logger.withTag(TAG).w { "Cognito revoke failed; the stored session is removed anyway" } }
        }
        tokenStore.clear()
    }

    private companion object {
        const val TAG = "SignOut"
    }
}
