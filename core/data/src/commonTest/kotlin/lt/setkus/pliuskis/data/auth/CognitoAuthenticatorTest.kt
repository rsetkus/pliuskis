package lt.setkus.pliuskis.data.auth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import lt.setkus.pliuskis.core.auth.AuthNetworkException
import lt.setkus.pliuskis.core.auth.AuthProvider
import lt.setkus.pliuskis.core.auth.AuthTokens
import lt.setkus.pliuskis.core.auth.SessionRejectedException
import lt.setkus.pliuskis.core.auth.SignInCancelledException

/** Test values only; no real pool, client or domain. */
private val settings = SignInSettings(
    userPoolId = "eu-north-1_TestPool",
    appClientId = "test-client",
    loginDomain = "test.auth.eu-north-1.amazoncognito.com",
    redirectUri = "lt.setkus.pliuskis.test://signin",
)

private val jsonHeaders = headersOf("Content-Type", "application/json")

/** A browser that returns the redirect [answer] builds from the authorize URL, and keeps the URLs it opened. */
private class FakeBrowser(private val answer: (authorizeUrl: Url) -> String) : AuthorizationBrowser {
    val opened = mutableListOf<Url>()

    override suspend fun authorize(url: String, redirectUri: String): String {
        val parsed = Url(url)
        opened += parsed
        return answer(parsed)
    }
}

/** The redirect of a successful sign-in: the code, and the state the authorize URL sent. */
private fun signedInRedirect(authorizeUrl: Url) =
    "${settings.redirectUri}?code=test-code&state=${authorizeUrl.parameters["state"]}"

private class Recorded(
    val url: String,
    val form: Map<String, String>,
    val headers: Map<String, String>,
    val body: String,
)

private fun authenticator(
    browser: AuthorizationBrowser = FakeBrowser(::signedInRedirect),
    recorded: MutableList<Recorded> = mutableListOf(),
    respond: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): CognitoAuthenticator {
    val engine = MockEngine { request ->
        val body = request.body.toByteArray().decodeToString()
        val form = parseQueryString(body).entries().associate { (key, values) -> key to values.firstOrNull().orEmpty() }
        val headers = request.headers.entries().associate { it.key to it.value.firstOrNull().orEmpty() }
        recorded += Recorded(request.url.toString(), form, headers, body)
        respond(request)
    }
    return CognitoAuthenticator(settings, browser, HttpClient(engine) { expectSuccess = false })
}

/**
 * The Cognito client behind the sign-in (STORY-1, PLIUS-34): the managed login URL with PKCE, the token exchange, the
 * refresh, revoke and DeleteUser calls, and how their failures map to the auth port's exceptions.
 */
class CognitoAuthenticatorTest : FunSpec({

    test("signInOpensTheProvidersManagedLoginWithPkceAndExchangesTheCode") {
        runTest {
            val browser = FakeBrowser(::signedInRedirect)
            val recorded = mutableListOf<Recorded>()
            val target = authenticator(browser, recorded) {
                respond(
                    """{"id_token":"id","access_token":"access","refresh_token":"refresh","expires_in":3600}""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
            }

            val tokens = target.signIn(AuthProvider.APPLE)

            tokens shouldBe AuthTokens("id", "access", "refresh")
            val authorize = browser.opened.single()
            authorize.toString() shouldStartWith "https://test.auth.eu-north-1.amazoncognito.com/oauth2/authorize"
            authorize.parameters["identity_provider"] shouldBe "SignInWithApple"
            authorize.parameters["response_type"] shouldBe "code"
            authorize.parameters["client_id"] shouldBe "test-client"
            authorize.parameters["redirect_uri"] shouldBe settings.redirectUri
            authorize.parameters["scope"] shouldBe SIGN_IN_SCOPES
            authorize.parameters["code_challenge_method"] shouldBe "S256"
            val exchange = recorded.single()
            exchange.url shouldBe "https://test.auth.eu-north-1.amazoncognito.com/oauth2/token"
            exchange.form["grant_type"] shouldBe "authorization_code"
            exchange.form["code"] shouldBe "test-code"
            exchange.form["redirect_uri"] shouldBe settings.redirectUri
            // The challenge in the URL is the S256 hash of the verifier sent with the code.
            val verifier = checkNotNull(exchange.form["code_verifier"])
            verifier.length shouldBe 64
            authorize.parameters["code_challenge"] shouldBe kotlin.io.encoding.Base64.UrlSafe
                .withPadding(kotlin.io.encoding.Base64.PaddingOption.ABSENT)
                .encode(sha256(verifier.encodeToByteArray()))
        }
    }

    test("googleSignInAsksForTheGoogleProvider") {
        runTest {
            val browser = FakeBrowser(::signedInRedirect)
            val target = authenticator(browser) {
                respond(
                    """{"id_token":"id","access_token":"access","refresh_token":"refresh"}""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
            }

            target.signIn(AuthProvider.GOOGLE)

            browser.opened.single().parameters["identity_provider"] shouldBe "Google"
        }
    }

    test("aClosedBrowserIsCancelledAndCallsNoTokenEndpoint") {
        runTest {
            val recorded = mutableListOf<Recorded>()
            val browser = object : AuthorizationBrowser {
                override suspend fun authorize(url: String, redirectUri: String): String =
                    throw SignInCancelledException()
            }
            val target = authenticator(browser, recorded) { respond("", HttpStatusCode.OK) }

            shouldThrow<SignInCancelledException> { target.signIn(AuthProvider.GOOGLE) }
            recorded shouldHaveSize 0
        }
    }

    test("aRedirectForAnotherSignInIsRefused") {
        runTest {
            val recorded = mutableListOf<Recorded>()
            val browser = FakeBrowser { "${settings.redirectUri}?code=test-code&state=other" }
            val target = authenticator(browser, recorded) { respond("", HttpStatusCode.OK) }

            shouldThrow<IllegalStateException> { target.signIn(AuthProvider.GOOGLE) }
            recorded shouldHaveSize 0
        }
    }

    test("refreshKeepsTheStoredRefreshTokenWhenCognitoSendsNone") {
        runTest {
            val recorded = mutableListOf<Recorded>()
            val target = authenticator(recorded = recorded) {
                respond("""{"id_token":"new-id","access_token":"new-access"}""", HttpStatusCode.OK, jsonHeaders)
            }

            target.refresh("stored-refresh") shouldBe AuthTokens("new-id", "new-access", "stored-refresh")
            recorded.single().form shouldBe mapOf(
                "grant_type" to "refresh_token",
                "client_id" to "test-client",
                "refresh_token" to "stored-refresh",
            )
        }
    }

    test("aRejectedRefreshTokenIsSessionRejected") {
        runTest {
            val target = authenticator {
                respond("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest, jsonHeaders)
            }

            shouldThrow<SessionRejectedException> { target.refresh("expired") }
        }
    }

    test("anUnreachableCognitoIsANetworkError") {
        runTest {
            val target = authenticator { throw IOException("no route to host") }

            shouldThrow<AuthNetworkException> { target.refresh("stored-refresh") }
        }
    }

    test("revokeSendsTheRefreshTokenToTheRevokeEndpoint") {
        runTest {
            val recorded = mutableListOf<Recorded>()
            val target = authenticator(recorded = recorded) { respond("", HttpStatusCode.OK) }

            target.revoke("stored-refresh")

            val revoke = recorded.single()
            revoke.url shouldBe "https://test.auth.eu-north-1.amazoncognito.com/oauth2/revoke"
            revoke.form shouldBe mapOf("token" to "stored-refresh", "client_id" to "test-client")
        }
    }

    test("deleteUserCallsTheUserPoolApiOfThePoolsRegionWithTheAccessToken") {
        runTest {
            val recorded = mutableListOf<Recorded>()
            val target = authenticator(recorded = recorded) { respond("{}", HttpStatusCode.OK) }

            target.deleteUser("access")

            val delete = recorded.single()
            delete.url shouldBe "https://cognito-idp.eu-north-1.amazonaws.com/"
            delete.headers["X-Amz-Target"] shouldBe "AWSCognitoIdentityProviderService.DeleteUser"
            delete.body shouldBe """{"AccessToken":"access"}"""
        }
    }

    test("aFailedDeleteUserThrows") {
        runTest {
            val target = authenticator { respond("""{"__type":"NotAuthorizedException"}""", HttpStatusCode.BadRequest) }

            shouldThrow<IllegalStateException> { target.deleteUser("access") }
        }
    }
})
