package lt.setkus.pliuskis.data.aws

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest

/**
 * STORY-2 AC42 and AC44 (shared part): the signed-in identity's temporary credentials for every connect attempt. An
 * attempt is what both platform clients do before they open a socket: `clientIdForAttempt` (identity ID and fresh
 * credentials), then `signedUrlNow` (the URL the WebSocket opens). The identity pool is a Ktor mock that counts calls.
 */
class Story2AwsIotCredentialsTest : FunSpec({

    // AC42
    // GIVEN cached credentials that expire 4 minutes after the clock
    // WHEN the next connection attempt is prepared and signed
    // THEN new credentials come from Cognito first, and X-Amz-Credential carries the new access key ID
    test("testStory2_credentialsExpiringWithinFiveMinutesAreRefreshedBeforeSigning") {
        runTest {
            val clock = FakeClock()
            val pool = FakeCognitoIdentityPool(clock, nextAccessKeyIds = listOf(TEST_ACCESS_KEY_ID, "AKIDNEWKEY"))
            val cache = testCredentialsCache(pool, InMemoryTokenStore(), backgroundScope)
            cache.clientIdForAttempt(INSTALL_SUFFIX)
            cache.signedUrlNow().credentialAccessKeyId shouldBe TEST_ACCESS_KEY_ID

            clock.epochSeconds += ONE_HOUR - 4 * ONE_MINUTE
            cache.clientIdForAttempt(INSTALL_SUFFIX)
            val url = cache.signedUrlNow()

            pool.getCredentialsCalls shouldBe 2
            url.credentialAccessKeyId shouldBe "AKIDNEWKEY"
        }
    }

    // AC42
    // GIVEN cached credentials that expired a minute ago
    // WHEN the next connection attempt is prepared and signed
    // THEN new credentials come from Cognito first, and X-Amz-Credential carries the new access key ID
    test("testStory2_expiredCredentialsAreRefreshedBeforeSigning") {
        runTest {
            val clock = FakeClock()
            val pool = FakeCognitoIdentityPool(clock, nextAccessKeyIds = listOf(TEST_ACCESS_KEY_ID, "AKIDNEWKEY"))
            val cache = testCredentialsCache(pool, InMemoryTokenStore(), backgroundScope)
            cache.clientIdForAttempt(INSTALL_SUFFIX)

            clock.epochSeconds += ONE_HOUR + ONE_MINUTE
            cache.clientIdForAttempt(INSTALL_SUFFIX)
            val url = cache.signedUrlNow()

            pool.getCredentialsCalls shouldBe 2
            url.credentialAccessKeyId shouldBe "AKIDNEWKEY"
        }
    }

    // AC42
    // GIVEN cached credentials that expire 10 minutes after the clock
    // WHEN the next connection attempt is prepared and signed
    // THEN it signs with the cached credentials and makes no Cognito call
    test("testStory2_credentialsValidForMoreThanFiveMinutesAreUsedWithoutACognitoCall") {
        runTest {
            val clock = FakeClock()
            val pool = FakeCognitoIdentityPool(clock, nextAccessKeyIds = listOf(TEST_ACCESS_KEY_ID, "AKIDNEWKEY"))
            val cache = testCredentialsCache(pool, InMemoryTokenStore(), backgroundScope)
            cache.clientIdForAttempt(INSTALL_SUFFIX)
            val callsAfterFirstAttempt = pool.totalCalls

            clock.epochSeconds += ONE_HOUR - 10 * ONE_MINUTE
            cache.clientIdForAttempt(INSTALL_SUFFIX)
            val url = cache.signedUrlNow()

            pool.totalCalls shouldBe callsAfterFirstAttempt
            url.credentialAccessKeyId shouldBe TEST_ACCESS_KEY_ID
        }
    }

    // AC44 (shared part; the platform clients' "no socket" is in their own tests)
    // GIVEN STORY-1's session is signed out (no stored tokens)
    // WHEN a connection attempt is prepared
    // THEN it gives no client ID and makes no Cognito identity pool call
    test("testStory2_signedOutAttemptGivesNoClientIdAndMakesNoCognitoCall") {
        runTest {
            val clock = FakeClock()
            val pool = FakeCognitoIdentityPool(clock)
            val cache = testCredentialsCache(pool, InMemoryTokenStore(initial = null), backgroundScope)

            cache.clientIdForAttempt(INSTALL_SUFFIX) shouldBe null

            pool.totalCalls shouldBe 0
        }
    }
})
