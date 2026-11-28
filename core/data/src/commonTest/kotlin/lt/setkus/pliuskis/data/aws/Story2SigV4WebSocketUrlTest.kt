package lt.setkus.pliuskis.data.aws

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith

private val ac37Credentials = AwsCredentials(
    accessKeyId = TEST_ACCESS_KEY_ID,
    secretAccessKey = TEST_SECRET_KEY,
    sessionToken = TEST_SESSION_TOKEN,
    expiresAtEpochSeconds = NOV_21_12_00_UTC + ONE_HOUR,
)

/**
 * The expected URL for the AC37 inputs, from an independent reference signer: botocore's `SigV4Auth`
 * (`canonical_request`, `string_to_sign`, `signature`) of botocore 2.0.0dev155, the copy bundled with AWS CLI 2.42.1,
 * signing `GET /mqtt` with the four signed query parameters and the `host` header, without the session token, which
 * is then appended after `X-Amz-Signature` (the form STORY-4 found AWS IoT accepts). The script and its output are in
 * STORY-2.implementation.md (AC37).
 */
private const val REFERENCE_URL =
    "wss://a1b2c3d4e5f6g7-ats.iot.eu-north-1.amazonaws.com/mqtt?X-Amz-Algorithm=AWS4-HMAC-SHA256" +
        "&X-Amz-Credential=AKIDEXAMPLE%2F20261121%2Feu-north-1%2Fiotdevicegateway%2Faws4_request" +
        "&X-Amz-Date=20261121T120000Z&X-Amz-SignedHeaders=host" +
        "&X-Amz-Signature=9385e77cd5e6c697e099caebee0c4c9600f5492e5357ce9c629f0194c3eabf76" +
        "&X-Amz-Security-Token=AQoD%2Babc%2Fdef%3D"

private fun signAc37(): String = signAwsIotWebSocketUrl(
    credentials = ac37Credentials,
    endpoint = TEST_ENDPOINT,
    region = TEST_REGION,
    signingTimeEpochSeconds = NOV_21_12_00_UTC,
    tokenPlacement = SecurityTokenPlacement.APPENDED,
)

/**
 * STORY-2 AC37, AC38 (and AC39: this spec is in commonTest, so it runs in both `testAndroidHostTest` and
 * `iosSimulatorArm64Test` of core:data): the shared SigV4 signer of AWS IoT's MQTT over WebSocket URL.
 */
class Story2SigV4WebSocketUrlTest : FunSpec({

    // AC37
    // GIVEN the AC37 key, secret, session token, region, endpoint and signing time 2026-11-21T12:00:00Z
    // WHEN the URL is signed
    // THEN it starts with wss://<endpoint>/mqtt? and has the four fixed SigV4 query parameters
    test("testStory2_signedUrlTargetsTheEndpointMqttPathWithTheSigV4QueryParameters") {
        val url = signAc37()

        url shouldStartWith "wss://a1b2c3d4e5f6g7-ats.iot.eu-north-1.amazonaws.com/mqtt?"
        url.queryParameter("X-Amz-Algorithm") shouldBe "AWS4-HMAC-SHA256"
        url.queryParameter("X-Amz-Credential") shouldBe
            "AKIDEXAMPLE%2F20261121%2Feu-north-1%2Fiotdevicegateway%2Faws4_request"
        url.queryParameter("X-Amz-Date") shouldBe "20261121T120000Z"
        url.queryParameter("X-Amz-SignedHeaders") shouldBe "host"
    }

    // AC37
    // GIVEN the AC37 inputs
    // WHEN the URL is signed
    // THEN its X-Amz-Signature is the reference signer's, and the whole URL is the reference URL
    test("testStory2_signatureEqualsTheIndependentReferenceSigner") {
        val url = signAc37()

        url.queryParameter("X-Amz-Signature") shouldBe REFERENCE_URL.queryParameter("X-Amz-Signature")
        url shouldBe REFERENCE_URL
    }

    // AC38
    // GIVEN the AC37 inputs, whose session token AQoD+abc/def= has '+', '/' and '='
    // WHEN the URL is signed
    // THEN it has X-Amz-Security-Token=AQoD%2Babc%2Fdef%3D exactly once
    test("testStory2_sessionTokenIsPercentEncodedAndAppearsExactlyOnce") {
        val url = signAc37()

        url.queryParameterNames().count { it == "X-Amz-Security-Token" } shouldBe 1
        url.queryParameter("X-Amz-Security-Token") shouldBe "AQoD%2Babc%2Fdef%3D"
        url.split("X-Amz-Security-Token=AQoD%2Babc%2Fdef%3D").size shouldBe 2
    }

    // AC38
    // GIVEN the AC37 inputs
    // WHEN the URL is signed
    // THEN no query value has a raw '+', '/' or '='
    test("testStory2_noQueryValueHasARawPlusSlashOrEquals") {
        val url = signAc37()
        val values = url.substringAfter('?').split('&').map { it.substringAfter('=') }

        url.queryParameterNames() shouldContainAll listOf("X-Amz-Signature", "X-Amz-Security-Token")
        values.forEach { value ->
            value shouldNotContain "+"
            value shouldNotContain "/"
            value shouldNotContain "="
        }
    }
})
