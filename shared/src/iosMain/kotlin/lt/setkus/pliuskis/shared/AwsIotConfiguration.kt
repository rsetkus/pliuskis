package lt.setkus.pliuskis.shared

import lt.setkus.pliuskis.data.aws.AwsIotSettings
import lt.setkus.pliuskis.data.aws.SecurityTokenPlacement
import platform.Foundation.NSBundle

// The Info.plist keys of the AWS IoT connection over Cognito; the build fills them from `Secrets.xcconfig` (see
// `project.yml`): `COGNITO_IDENTITY_POOL_ID`, `AWS_REGION` and `AMAZON_IOT_ENDPOINT` of the active configuration.
private const val IDENTITY_POOL_ID_KEY = "CognitoIdentityPoolId"
private const val REGION_KEY = "AwsRegion"
private const val IOT_ENDPOINT_KEY = "AmazonIotEndpoint"

/**
 * The AWS IoT connection of this build's stack, from the app's Info.plist, with the session token in the signed URL
 * where [tokenPlacement] says. A missing key is empty; every connect attempt then fails and the device list shows its
 * error screen.
 */
internal fun awsIotSettingsFromInfoPlist(
    tokenPlacement: SecurityTokenPlacement,
    bundle: NSBundle = NSBundle.mainBundle,
): AwsIotSettings = AwsIotSettings(
    identityPoolId = bundle.infoString(IDENTITY_POOL_ID_KEY),
    region = bundle.infoString(REGION_KEY),
    endpoint = bundle.infoString(IOT_ENDPOINT_KEY),
    tokenPlacement = tokenPlacement,
)

private fun NSBundle.infoString(key: String): String = objectForInfoDictionaryKey(key) as? String ?: ""
