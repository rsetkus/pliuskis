package lt.setkus.pliuskis.core

import kotlinx.coroutines.flow.Flow

interface BaseUseCase<in T, out R> : (T) -> Flow<R>