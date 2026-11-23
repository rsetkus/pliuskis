package lt.setkus.pliuskis.core.auth

import arrow.core.Either
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import lt.setkus.pliuskis.core.BaseUseCase

/**
 * Signs the user in with the chosen provider through Cognito's managed login and stores the tokens it returns. The
 * failure is the [Authenticator]'s: [SignInCancelledException], [AuthNetworkException] or any other.
 */
class SignInUseCase(
    private val authenticator: Authenticator,
    private val tokenStore: TokenStore,
) : BaseUseCase<AuthProvider, Unit> {

    override fun invoke(param: AuthProvider): Flow<Either<Throwable, Unit>> = flow {
        emit(Either.catch { tokenStore.save(authenticator.signIn(param)) })
    }
}
