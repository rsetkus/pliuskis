package lt.setkus.pliuskis.data.aws

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import lt.setkus.pliuskis.core.auth.AuthProvider
import lt.setkus.pliuskis.core.auth.AuthTokens
import lt.setkus.pliuskis.core.auth.Authenticator
import lt.setkus.pliuskis.core.auth.TokenStore

/*
 * Test values and fakes for the AWS IoT connection over Cognito (STORY-2). No real key, token, pool or endpoint: the
 * key, secret and session token are the AC37 examples, the identity IDs are made up.
 */

const val TEST_ACCESS_KEY_ID = "AKIDEXAMPLE"
const val TEST_SECRET_KEY = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY"
const val TEST_SESSION_TOKEN = "AQoD+abc/def="
const val TEST_REGION = "eu-north-1"
const val TEST_ENDPOINT = "a1b2c3d4e5f6g7-ats.iot.eu-north-1.amazonaws.com"
const val TEST_IDENTITY_POOL_ID = "eu-north-1:00000000-0000-4000-8000-00000000test"
const val TEST_USER_POOL_ID = "eu-north-1_TestPool"
const val TEST_ID_TOKEN = "eyJtest.idtoken.sig"
const val IDENTITY_A = "eu-north-1:1b2c3d4e-0000-4000-8000-000000000001"
const val IDENTITY_B = "eu-north-1:1b2c3d4e-0000-4000-8000-000000000002"
const val INSTALL_SUFFIX = "testA"

/** 2026-11-21T12:00:00Z in seconds since the epoch, the signing time of AC37. */
const val NOV_21_12_00_UTC = 1_795_262_400L
const val ONE_MINUTE = 60L
const val ONE_HOUR = 3_600L

/** The user pool tokens of the account whose ID token is [idToken]. */
fun tokensOf(idToken: String = TEST_ID_TOKEN): AuthTokens =
    AuthTokens(idToken = idToken, accessToken = "test-access-token", refreshToken = "test-refresh-token")

/** The wall clock of a test; tests move it forward by hand. */
class FakeClock(var epochSeconds: Long = NOV_21_12_00_UTC) {
    fun now(): Long = epochSeconds
}

/** The stored session: what a signed-in (or signed-out) app has in its Keychain or Keystore file. */
class InMemoryTokenStore(initial: AuthTokens? = tokensOf()) : TokenStore {
    private val tokens = MutableStateFlow(initial)

    override fun observe(): Flow<AuthTokens?> = tokens

    override suspend fun save(tokens: AuthTokens) {
        this.tokens.value = tokens
    }

    override suspend fun clear() {
        tokens.value = null
    }

    /** The user signs out (or signs in with another account, when [next] is given). */
    fun replaceWith(next: AuthTokens?) {
        tokens.value = next
    }
}

/** Gives [refreshed] for every refresh; the other calls are not used by the AWS IoT connection. */
class FakeAuthenticator(private val refreshed: AuthTokens = tokensOf("eyJtest.refreshed.sig")) : Authenticator {
    var refreshCalls = 0
        private set

    override suspend fun signIn(provider: AuthProvider): AuthTokens = error("not used")

    override suspend fun refresh(refreshToken: String): AuthTokens {
        refreshCalls++
        return refreshed
    }

    override suspend fun revoke(refreshToken: String) = Unit

    override suspend fun deleteUser(accessToken: String) = Unit
}

/** Whether the fake identity pool answers, and how it fails. */
enum class IdentityPoolAnswer { CREDENTIALS, NETWORK_ERROR, NOT_AUTHORIZED }

/**
 * The Cognito identity pool API (`GetId`, `GetCredentialsForIdentity`) behind a Ktor [MockEngine]. It counts the calls
 * of each action, maps each ID token to an identity ID ([identities], [IDENTITY_A] for any other token), and gives new
 * credentials on every `GetCredentialsForIdentity`: access key ID [nextAccessKeyIds] in turn (then `AKIDTESTn`), the
 * AC37 secret and session token, expiring [credentialsLifetimeSeconds] after [clock]'s time. With [dispatcher] (a
 * test dispatcher), the requests run in the test's virtual time.
 */
class FakeCognitoIdentityPool(
    val clock: FakeClock,
    dispatcher: CoroutineDispatcher? = null,
    var answer: IdentityPoolAnswer = IdentityPoolAnswer.CREDENTIALS,
    var credentialsLifetimeSeconds: Long = ONE_HOUR,
    val identities: MutableMap<String, String> = mutableMapOf(),
    nextAccessKeyIds: List<String> = listOf(TEST_ACCESS_KEY_ID),
) {
    private val accessKeyIds = ArrayDeque(nextAccessKeyIds)
    private var issued = 0

    var getIdCalls = 0
        private set
    var getCredentialsCalls = 0
        private set

    /** Every request to the identity pool, GetId and GetCredentialsForIdentity. */
    val totalCalls: Int get() = getIdCalls + getCredentialsCalls

    private val handler: MockRequestHandler = { request ->
        val target = request.headers["X-Amz-Target"].orEmpty()
        val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
        val idToken = body["Logins"]?.jsonObject?.values?.firstOrNull()?.jsonPrimitive?.content.orEmpty()
        if (target.endsWith("GetId")) getIdCalls++ else getCredentialsCalls++
        when (answer) {
            IdentityPoolAnswer.NETWORK_ERROR -> throw IOException("No network (test)")
            IdentityPoolAnswer.NOT_AUTHORIZED -> respond(
                content = """{"__type":"NotAuthorizedException","message":"Invalid login token."}""",
                status = HttpStatusCode.BadRequest,
                headers = amzJson,
            )
            IdentityPoolAnswer.CREDENTIALS -> respond(
                content = if (target.endsWith("GetId")) {
                    """{"IdentityId":"${identities[idToken] ?: IDENTITY_A}"}"""
                } else {
                    issueCredentials()
                },
                status = HttpStatusCode.OK,
                headers = amzJson,
            )
        }
    }

    val engine = MockEngine(
        MockEngineConfig().apply {
            addHandler(handler)
            this.dispatcher = dispatcher
        },
    )

    private fun issueCredentials(): String {
        issued++
        val accessKeyId = accessKeyIds.removeFirstOrNull() ?: "AKIDTEST$issued"
        val expiration = clock.now() + credentialsLifetimeSeconds
        return """{"IdentityId":"x","Credentials":{"AccessKeyId":"$accessKeyId","SecretKey":"$TEST_SECRET_KEY",""" +
            """"SessionToken":"$TEST_SESSION_TOKEN","Expiration":$expiration}}"""
    }

    private companion object {
        val amzJson = headersOf("Content-Type", "application/x-amz-json-1.1")
    }
}

/** The AWS IoT connection settings of a test stack, with the token placement STORY-4 found AWS IoT accepts. */
fun testAwsIotSettings(endpoint: String = TEST_ENDPOINT): AwsIotSettings = AwsIotSettings(
    identityPoolId = TEST_IDENTITY_POOL_ID,
    region = TEST_REGION,
    endpoint = endpoint,
    tokenPlacement = SecurityTokenPlacement.APPENDED,
)

/** The production credentials cache over the fake identity pool, the fake session and the pool's fake clock. */
fun testCredentialsCache(
    pool: FakeCognitoIdentityPool,
    tokenStore: TokenStore,
    scope: CoroutineScope,
    authenticator: Authenticator = FakeAuthenticator(),
    settings: AwsIotSettings = testAwsIotSettings(),
): AwsIotCredentialsCache = AwsIotCredentialsCache(
    settings = settings,
    identity = CognitoIdentityClient(settings, TEST_USER_POOL_ID, HttpClient(pool.engine)),
    tokenStore = tokenStore,
    authenticator = authenticator,
    scope = scope,
    now = pool.clock::now,
)

/** The raw (still percent-encoded) value of query parameter [name] of a URL or a query string, or null. */
fun String.queryParameter(name: String): String? =
    substringAfter('?')
        .split('&')
        .map { it.substringBefore('=') to it.substringAfter('=', "") }
        .firstOrNull { it.first == name }
        ?.second

/** Every query parameter name of a URL or a query string, in order, repeats included. */
fun String.queryParameterNames(): List<String> = substringAfter('?').split('&').map { it.substringBefore('=') }

/** The `X-Amz-Date` of a signed URL or query string. */
val String.signingDate: String? get() = queryParameter("X-Amz-Date")

/** The access key ID in the `X-Amz-Credential` of a signed URL or query string. */
val String.credentialAccessKeyId: String? get() = queryParameter("X-Amz-Credential")?.substringBefore("%2F")
