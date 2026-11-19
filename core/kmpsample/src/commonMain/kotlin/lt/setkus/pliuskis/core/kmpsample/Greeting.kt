package lt.setkus.pliuskis.core.kmpsample

/** Name of the platform the shared code runs on. */
expect fun platformName(): String

/** A greeting that names the platform; proves that common code calls the platform actuals. */
fun greeting(): String = "Hello, ${platformName()}!"
