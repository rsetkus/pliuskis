package lt.setkus.pliuskis.data.wire

/**
 * STORY-1: literal MQTT payloads and topics the app sends or subscribes to, as the Pico firmware
 * (`rsetkus/pico-pliuskis`) expects them. Only `const val` strings, so the object compiles unchanged in a
 * `commonTest` source set.
 */
object DataWireFixtures {
    const val CLIENT_ID = "app-1"
    const val DEVICE_ID = "pico-1"

    const val STATUS_REQUEST_PAYLOAD = """{"applicationId":"app-1"}"""
    const val STATUS_REQUEST_TOPIC = "devices/pico-1/status"

    const val WATER_COMMAND_PAYLOAD = """{"command":"water"}"""
    const val WATER_COMMAND_TOPIC = "devices/pico-1/command"

    const val SYSTEM_STATUS_TOPIC = "application/app-1/pico-1/status"

    const val INFO_TOPIC_FILTER = "devices/+/info"
    const val AVAILABILITY_TOPIC_FILTER = "devices/+/availability"
}
