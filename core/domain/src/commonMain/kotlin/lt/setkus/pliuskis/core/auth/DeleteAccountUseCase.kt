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
 */
class DeleteAccountUseCase(
    private val authenticator: Authenticator,
    private val tokenStore: TokenStore,
) : BaseUseCase<Unit, Unit> {

    override fun invoke(param: Unit): Flow<Either<Throwable, Unit>> = flow {
        emit(Either.catch { deleteAccount() })
    }

    private suspend fun deleteAccount() {
        val stored = checkNotNull(tokenStore.observe().first()) { "No signed-in user to delete" }
        authenticator.deleteUser(stored.accessToken)
        tokenStore.clear()
    }
}
