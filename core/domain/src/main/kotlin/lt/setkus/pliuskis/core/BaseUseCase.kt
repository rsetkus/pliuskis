package lt.setkus.pliuskis.core

import arrow.core.Either
import kotlinx.coroutines.flow.Flow

interface BaseUseCase<in T, out R> : (T) -> Flow<Either<Throwable, R>>
