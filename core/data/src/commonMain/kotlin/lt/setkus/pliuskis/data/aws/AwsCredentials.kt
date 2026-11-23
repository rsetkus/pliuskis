package lt.setkus.pliuskis.data.aws

/**
 * Temporary AWS credentials of one Cognito identity, from the identity pool's `GetCredentialsForIdentity`. [toString]
 * prints no value, so the credentials can't reach a log by accident.
 *
 * @property expiresAtEpochSeconds when AWS stops accepting them, in seconds since 1970-01-01T00:00:00Z
 */
class AwsCredentials(
    val accessKeyId: String,
    val secretAccessKey: String,
    val sessionToken: String,
    val expiresAtEpochSeconds: Long,
) {
    override fun toString(): String = "AwsCredentials(<redacted>, expiresAt=$expiresAtEpochSeconds)"
}
