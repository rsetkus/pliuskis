package lt.setkus.pliuskis.core.auth

import arrow.core.Either
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import lt.setkus.pliuskis.core.BaseUseCase

/**
 * Checks the stored session at launch; the result is whether someone is signed in. With no stored tokens: false.
 * Otherwise Cognito refreshes the stored refresh token and the new tokens are stored: true. When Cognito rejects the
 * refresh token (expired or revoked), the stored session is removed: false. Any other failure (no network, Cognito
 * down) keeps the stored session, so a launch without network stays signed in: true.
 */
class RestoreSessionUseCase(
    private val authenticator: Authenticator,
    private val tokenStore: TokenStore,
) : BaseUseCase<Unit, Boolean> {

    override fun invoke(param: Unit): Flow<Either<Throwable, Boolean>> = flow {
        emit(Either.catch { restore() })
    }

    private suspend fun restore(): Boolean {
        val stored = tokenStore.observe().first()
        return stored != null && refresh(stored)
    }

    private suspend fun refresh(stored: AuthTokens): Boolean =
        Either.catch { authenticator.refresh(stored.refreshToken) }.fold(
            ifLeft = { error -> keepUnlessRejected(error) },
            ifRight = { tokens ->
                tokenStore.save(tokens)
                true
            },
        )

    private suspend fun keepUnlessRejected(error: Throwable): Boolean {
        val rejected = error is SessionRejectedException
        if (rejected) {
            tokenStore.clear()
        }
        return !rejected
    }
}
