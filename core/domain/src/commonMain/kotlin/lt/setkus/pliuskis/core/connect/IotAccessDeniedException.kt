package lt.setkus.pliuskis.core.connect

/**
 * The MQTT broker answered but refused the signed-in identity: on AWS IoT, an identity that has no AWS IoT policy yet
 * (the admin has not approved it). It is not a network error: the device list shows the empty list for it, not the
 * error screen, and does not retry on its own.
 */
class IotAccessDeniedException : Exception("The broker refused the signed-in identity: no AWS IoT access")
