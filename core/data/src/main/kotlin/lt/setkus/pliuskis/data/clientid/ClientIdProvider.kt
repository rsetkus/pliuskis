package lt.setkus.pliuskis.data.clientid

import kotlin.random.Random

/** 23 characters is the longest client ID that MQTT 3.1.1 obliges every broker to accept. */
private const val CLIENT_ID_LENGTH = 23
private const val CLIENT_ID_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
private val VALID_CLIENT_ID = Regex("^[0-9a-zA-Z]{1,$CLIENT_ID_LENGTH}$")

/**
 * Generates a random MQTT client ID: 23 characters of `0-9a-zA-Z`, so any broker accepts it and it is safe inside a
 * topic (no `/`, `+`, `#`).
 */
fun generateClientId(): String =
    (1..CLIENT_ID_LENGTH)
        .map { CLIENT_ID_ALPHABET[Random.nextInt(CLIENT_ID_ALPHABET.length)] }
        .joinToString("")

/**
 * Gives this app install its MQTT client ID: the stored one if it is valid, otherwise a newly generated one, which is
 * stored so that later app processes use the same ID.
 */
class ClientIdProvider(private val store: ClientIdStore) {

    fun clientId(): String {
        val stored = store.read()
        val clientId = if (stored != null && VALID_CLIENT_ID.matches(stored)) {
            stored
        } else {
            generateClientId().also(store::write)
        }
        return clientId
    }
}
