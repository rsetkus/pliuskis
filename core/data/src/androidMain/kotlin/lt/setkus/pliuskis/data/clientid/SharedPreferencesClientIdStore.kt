package lt.setkus.pliuskis.data.clientid

import android.content.Context

private const val PREFERENCES_NAME = "mqtt_client"
private const val CLIENT_ID_KEY = "client_id"

/** Android [ClientIdStore]: keeps the client ID in the app's private SharedPreferences. */
class SharedPreferencesClientIdStore(context: Context) : ClientIdStore {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): String? = preferences.getString(CLIENT_ID_KEY, null)

    override fun write(clientId: String) {
        preferences.edit().putString(CLIENT_ID_KEY, clientId).apply()
    }
}
