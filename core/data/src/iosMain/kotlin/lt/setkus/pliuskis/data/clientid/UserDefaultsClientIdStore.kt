package lt.setkus.pliuskis.data.clientid

import platform.Foundation.NSUserDefaults

private const val CLIENT_ID_KEY = "lt.setkus.pliuskis.mqtt.clientId"

/**
 * iOS [ClientIdStore]: keeps the client ID in [defaults], the app's standard UserDefaults unless a test passes its own
 * domain. Deleting the app clears it, so a reinstall gets a new ID, as on Android.
 */
class UserDefaultsClientIdStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : ClientIdStore {

    override fun read(): String? = defaults.stringForKey(CLIENT_ID_KEY)

    override fun write(clientId: String) {
        defaults.setObject(clientId, forKey = CLIENT_ID_KEY)
    }
}
