package lt.setkus.pliuskis.data.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout

private const val REQUEST_TIMEOUT_MILLIS = 20_000L
private const val CONNECT_TIMEOUT_MILLIS = 10_000L

/**
 * The HTTP client of [CognitoAuthenticator] on [engine] (Android's or Darwin's). It installs no logging, so no token
 * reaches a log, and it never throws for an HTTP status: the authenticator reads the status itself.
 */
internal fun <T : HttpClientEngineConfig> cognitoHttpClient(engine: HttpClientEngineFactory<T>): HttpClient =
    HttpClient(engine) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
        }
    }
