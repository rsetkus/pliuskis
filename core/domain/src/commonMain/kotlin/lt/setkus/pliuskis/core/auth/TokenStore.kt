package lt.setkus.pliuskis.core.auth

import kotlinx.coroutines.flow.Flow

/**
 * The stored session (the token-store port): Android Keystore-backed storage on Android, the Keychain on iOS. Never
 * plain storage.
 */
interface TokenStore {
    /** The stored tokens, or null when no one is signed in. Emits the current value first, then after every change. */
    fun observe(): Flow<AuthTokens?>

    /** Stores [tokens], replacing any stored ones. */
    suspend fun save(tokens: AuthTokens)

    /** Removes the stored tokens. */
    suspend fun clear()
}
