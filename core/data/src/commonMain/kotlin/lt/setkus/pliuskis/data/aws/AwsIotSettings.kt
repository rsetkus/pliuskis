package lt.setkus.pliuskis.data.aws

/**
 * The AWS IoT connection of one stack over Cognito: its identity pool, region and AWS IoT endpoint. Each app reads the
 * values from its gitignored configuration; none is in the repository.
 *
 * @property identityPoolId the identity pool ID, `<region>:<uuid>`
 * @property region the AWS region of the identity pool and the AWS IoT endpoint, e.g. `eu-north-1`
 * @property endpoint the AWS IoT data endpoint, a host name without `wss://`, e.g.
 *   `<id>-ats.iot.<region>.amazonaws.com`
 * @property tokenPlacement where the signed URL carries the session token
 */
class AwsIotSettings(
    val identityPoolId: String,
    val region: String,
    val endpoint: String,
    val tokenPlacement: SecurityTokenPlacement,
) {
    /** False when a value is missing, for example in a build without a configured stack. */
    val isComplete: Boolean
        get() = listOf(identityPoolId, region, endpoint).none(String::isBlank) && identityPoolId.contains(':')
}
