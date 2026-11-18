package lt.setkus.pliuskis.core.wire

/**
 * STORY-1: literal MQTT payloads the Pico firmware (`rsetkus/pico-pliuskis`) sends to the app.
 * Only `const val` strings, so the object compiles unchanged in a `commonTest` source set.
 */
object DomainWireFixtures {
    const val DEVICE_ID = "pico-1"

    const val STATUS_PAYLOAD =
        """{"humidity":55,"waterLevel":70,"timestamp":{"epochSeconds":1700000000,"nanosecondsOfSecond":5}}"""

    const val INFO_PAYLOAD = """{"name":"Basil","deviceId":"pico-1","type":"plant"}"""

    const val INFO_PAYLOAD_UNKNOWN_TYPE = """{"name":"Basil","deviceId":"pico-1","type":"cactus"}"""

    const val INFO_PAYLOAD_EXTRA_KEY =
        """{"name":"Basil","deviceId":"pico-1","type":"plant","firmware":"1.2.3"}"""
}
