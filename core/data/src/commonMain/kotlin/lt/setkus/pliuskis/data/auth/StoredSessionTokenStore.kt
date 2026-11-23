package lt.setkus.pliuskis.data.auth

import arrow.core.Either
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import lt.setkus.pliuskis.core.auth.AuthTokens
import lt.setkus.pliuskis.core.auth.TokenStore

/**
 * Where a platform keeps the session's text in secure storage: Android Keystore-backed encryption, the iOS Keychain.
 * Never plain storage such as SharedPreferences or UserDefaults.
 */
internal interface SecureSessionStorage {
    /** The stored text, or null when nothing is stored. A value that can't be read is removed and is null. */
    fun read(): String?

    /** Stores [session], replacing what was stored. */
    fun write(session: String)

    /** Removes what is stored. */
    fun delete()
}

/**
 * The [TokenStore] over a platform's [storage]: the tokens as JSON, read once on first use and kept in memory after
 * that, so [observe] emits the current value at once and again after each [save] and [clear]. The storage is used
 * on [io] (it may block).
 */
internal class StoredSessionTokenStore(
    private val storage: SecureSessionStorage,
    private val io: CoroutineDispatcher,
) : TokenStore {

    private val mutex = Mutex()
    private val tokens = MutableStateFlow<AuthTokens?>(null)
    private var loaded = false

    override fun observe(): Flow<AuthTokens?> = flow {
        load()
        emitAll(tokens)
    }

    override suspend fun save(tokens: AuthTokens) {
        mutex.withLock {
            withContext(io) { storage.write(encodeSession(tokens)) }
            this.tokens.value = tokens
            loaded = true
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            withContext(io) { storage.delete() }
            tokens.value = null
            loaded = true
        }
    }

    private suspend fun load() {
        mutex.withLock {
            if (!loaded) {
                tokens.value = withContext(io) { storage.read() }?.let(::decodeSession)
                loaded = true
            }
        }
    }
}

private val sessionJson = Json { ignoreUnknownKeys = true }

/** The stored form of [AuthTokens]. Not a data class, so its `toString` can't print a token. */
@Serializable
private class StoredSession(val idToken: String, val accessToken: String, val refreshToken: String)

internal fun encodeSession(tokens: AuthTokens): String =
    sessionJson.encodeToString(StoredSession(tokens.idToken, tokens.accessToken, tokens.refreshToken))

/** The tokens of a stored [session], or null when it can't be read (then the user signs in again). */
internal fun decodeSession(session: String): AuthTokens? =
    Either.catch { sessionJson.decodeFromString<StoredSession>(session) }
        .onLeft { Logger.withTag("TokenStore").w { "The stored session can't be read; signing in again" } }
        .getOrNull()
        ?.let { AuthTokens(it.idToken, it.accessToken, it.refreshToken) }
