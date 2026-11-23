package lt.setkus.pliuskis.core.auth

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import lt.setkus.pliuskis.core.BaseUseCase

/**
 * Whether someone is signed in, read from the stored session only (no Cognito call): true while tokens are stored. It
 * emits the current value first, then after every sign-in, sign-out and account deletion.
 */
class ObserveSessionUseCase(
    private val tokenStore: TokenStore,
) : BaseUseCase<Unit, Boolean> {

    override fun invoke(param: Unit): Flow<Either<Throwable, Boolean>> =
        tokenStore.observe()
            .map<AuthTokens?, Either<Throwable, Boolean>> { tokens -> (tokens != null).right() }
            .catch { error -> emit(error.left()) }
}
